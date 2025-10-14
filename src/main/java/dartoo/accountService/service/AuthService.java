package dartoo.accountService.service;

import dartoo.accountService.config.JwtConfig;
import dartoo.accountService.domain.RefreshToken;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.dto.TokenResponseDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.RefreshTokenRepository;
import dartoo.accountService.repository.UserEntityRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtConfig cfg;
    private final TokenService tokenService;
    private final UserEntityRepository userEntityRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    //자체 로그인 - AccessToken, RefreshToken 발급
    @Transactional
    public TokenResponseDto loginIssue(String email, String did, String userAgent, HttpServletResponse response){
        Instant now = Instant.now();

        UserEntity user = userEntityRepository.findByUserEmail(email).orElseThrow(()->new ApiException(USER_NOT_FOUND));

        //이미 만료된 RT 삭제
        refreshTokenRepository.deleteAllByUserEntityAndExpiredAtBefore(user,now);
        //기존에 활성화된 RefreshToken revoke하기
        refreshTokenRepository.findAllByUserEntityAndRevokedAtIsNullAndExpiredAtAfter(user,now)
                .forEach(rt->{rt.revoke(now); refreshTokenRepository.save(rt);});

        String accessToken = tokenService.createAccessToken(user.getUserEmail(),user.getNickname(),now,user.getRole());
        String refreshToken = tokenService.createRefreshToken(user.getUserEmail(),did,now);
        Instant expire = parseAndValidateRefresh(refreshToken).getExpiration().toInstant();

        refreshTokenRepository.save(RefreshToken.builder()
                        .userEntity(user)
                        .token(hashRt(refreshToken))
                        .createdAt(now)
                        .expiredAt(expire)
                        .did(did)
                        .userAgent(userAgent)
                .build());

        //httpOnly + secure 형태로 쿠키에 저장
        attachRefreshCookie(response,refreshToken,expire);

        return new TokenResponseDto(
                accessToken,cfg.getAccessTtlSeconds(),refreshToken,cfg.getRefreshTtlSeconds()
        );
    }

    //Refresh Token 회전 + AccessToken 재발급
    //자동 로그인 구현 여부에 따라 분기
    //isRestart = true; 자동 로그인 상황
    //isRestart = false; 일반 토큰 갱신 상황
    @Transactional
    public TokenResponseDto refresh(String refreshToken, boolean isRestart, HttpServletResponse response){
        Instant now = Instant.now();
        if(refreshToken==null || refreshToken.isBlank()){
            throw new ApiException(INVALID_REFRESH_TOKEN);
        }
        Claims claims = parseAndValidateRefresh(refreshToken);
        String email = claims.getSubject();
        String deviceId = Optional.ofNullable(claims.get("did", String.class))
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new ApiException(INVALID_DEVICE_ID));


        //사용자 + 토큰 조회
        UserEntity userEntity = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()->new ApiException(USER_NOT_FOUND));
        RefreshToken rt = refreshTokenRepository.findByUserEntityAndToken(userEntity,hashRt(refreshToken))
                .orElseThrow(()-> new ApiException(REFRESH_TOKEN_NOT_FOUND));

        //검증
        if (rt.getRotatedAt()!=null)
            throw new ApiException(REFRESH_TOKEN_ALREADY_ROTATED);
        if (rt.getExpiredAt().isBefore(now))
            throw new ApiException(REFRESH_TOKEN_EXPIRED);

        //기존 Refresh Token 폐기 + 회전 표기
        rt.revoke(now);
        rt.rotate(now);

        //상황에 따라 다르게 (AccessToken 만료라서 재발급 받을 때 vs 재로그인 시 자동로그인으로 인한 Refresh Token 만료 연장)
        //refreshToken의 만료 시간을 정한다.
        Instant newExpire = isRestart ? now.plusSeconds(cfg.getRefreshTtlSeconds()) : rt.getExpiredAt();

        //새 AccessToken과 RefreshToken을 발급
        String newAccess =  tokenService.createAccessToken(email, userEntity.getNickname(), now, userEntity.getRole());
        //앱 재실행 -> 자동로그인 -> 만료 시간이 now + refreshTTL
        //단순 회전 -> 절대 만료 시간 유지
        String newRefresh = isRestart
                ? tokenService.createRefreshToken(email, deviceId, now)              // restart 하면 만료 시간 연장
                : tokenService.createRefreshTokenFixed(email, deviceId, newExpire);  // 그렇지 않으면 절대 만료 유지

        //새 RefreshToken 저장
        RefreshToken newRt = RefreshToken.builder()
                .userEntity(userEntity)
                .token(hashRt(newRefresh))
                .createdAt(now)
                .expiredAt(newExpire)
                .did(deviceId)
                .userAgent(rt.getUserAgent())
                .build();
        refreshTokenRepository.save(newRt);

        //RefreshToken을 쿠키에 담기
        attachRefreshCookie(response,newRefresh,newExpire);

        long refreshTtlLeft = Math.max(0, newExpire.getEpochSecond() - now.getEpochSecond());
        return new TokenResponseDto(newAccess,cfg.getAccessTtlSeconds(),newRefresh, refreshTtlLeft);
    }

    //RefreshToken을 쿠키에 추가 - HttpOnly + Secure
    public void attachRefreshCookie(HttpServletResponse response, String refreshToken, Instant expiration) {
            long maxAge = expiration.getEpochSecond()-Instant.now().getEpochSecond();
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(Math.max(0,maxAge))
                .path("/")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    //로그아웃 시, 해당 기기의 모든 RefreshToken을 revoke
    public void revokeRefreshToken(String refreshToken, String didFromHeader) {
        Instant now = Instant.now();

        // 토큰이 없거나 빈 값이면 그냥 리턴 (쿠키가 없을 수도 있음)
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        // Refresh Token 파싱 & 검증 후 현재 사용자 이메일 조회
        Claims claims = parseAndValidateRefresh(refreshToken);
        String email = claims.getSubject();
        String did = Optional.ofNullable(claims.get("did", String.class))
                .orElse(didFromHeader); //HTTP 헤더는 조작이 쉽기 때문.

        // 사용자 조회
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(() -> new ApiException(USER_NOT_FOUND));

        // 해당 기기(did)의 모든 활성화된 Refresh Token 조회 후 Revoke
        refreshTokenRepository.findAllByUserEntityAndDidAndRevokedAtIsNullAndExpiredAtAfter(user, did, now)
                .forEach(rt -> {
                    rt.revoke(now); // revokeAt 필드 세팅
                    refreshTokenRepository.save(rt);
                });
    }

    //DB에 저장 시 RefreshPepper을 추가해서 저장
    private String hashRt(String raw) {

        if(raw==null || raw.isBlank()){
            throw new ApiException(INVALID_REFRESH_TOKEN);
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(
                    Optional.ofNullable(cfg.getRefreshPepper()).orElse("").getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            mac.init(key);
            byte[] out = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out); // 64 hex
        } catch (NoSuchAlgorithmException e) {
            throw new ApiException(HMAC_256_NOT_AVAILABLE);
        } catch (InvalidKeyException e){
            throw new ApiException(INVALID_HMAC_KEY);
        }
    }

    //Jwt 검증 로직: AccessToken에 대한 검증은 SecurityConfig.java에서 만든 필터에서 함.
//    private Claims parseAndValidateAccess(String jwt){
//        try {
//            return Jwts.parserBuilder()
//                    .setSigningKey(cfg.getAccessKey())                         // HS256 대칭키
//                    .requireIssuer(cfg.getIssuer())            // 우리 발급자만 허용
//                    .setAllowedClockSkewSeconds(60)             // 60초 스큐 허용
//                    .build()
//                    .parseClaimsJws(jwt)
//                    .getBody();
//        } catch (JwtException e) { //invalid한 JWT일 경우 401 반환
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
//        }
//    }

    private Claims parseAndValidateRefresh(String jwt){
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(cfg.getRefreshKey())                         // HS256 대칭키
                    .requireIssuer(cfg.getIssuer())            // 우리 발급자만 허용
                    .setAllowedClockSkewSeconds(60)             // 60초 스큐 허용
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();
        } catch (ExpiredJwtException e){
            throw new ApiException(REFRESH_TOKEN_EXPIRED_JWT);
        } catch (JwtException | IllegalArgumentException e) { //invalid한 JWT일 경우 401 반환
            throw new ApiException(INVALID_REFRESH_TOKEN_JWT);
        }
    }

}
