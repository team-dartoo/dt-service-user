package dartoo.accountService.web;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.dto.account.LoginRequestDto;
import dartoo.accountService.dto.account.TokenResponseDto;
import dartoo.accountService.dto.account.TokenResponseDtoPublic;
import dartoo.accountService.dto.account.UserRequestDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.service.AuthService;
import dartoo.accountService.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.INVALID_CREDENTIALS;
import static dartoo.accountService.error.ErrorCode.MISSING_REFRESH_TOKEN;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
//사용자 자체 로그인 + 사용자 로그아웃 관련 컨트롤러
public class AuthController {
    private final UserService userService;
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    //이미 자체 로그인을 통해 가입한 회원인지 확인
    @PostMapping("/exist")
    public ResponseEntity<Boolean> userExists(
            @Validated(UserRequestDto.existGroup.class) @RequestBody UserRequestDto dto) {
        return ResponseEntity.ok(userService.existUser(dto));
    }

    //회원 가입
    @PostMapping("/signup")
    public ResponseEntity<Map<String,String>> signUp(
            @Validated(UserRequestDto.addGroup.class) @RequestBody UserRequestDto dto
    ){
        Long userId = userService.addUser(dto);
        UserEntity userEntity = userService.getUserById(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("email", userEntity.getUserEmail()));
    }

    //자체 로그인 - Access Token은 JSON으로 반환. Refresh Token은 HttpOnly + Secure 쿠키로 반환.
    //리턴 문에는 AccessToken만 있으면 됨.
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDtoPublic> login(@Validated @RequestBody LoginRequestDto dto, HttpServletRequest request, HttpServletResponse response){
        //인증 절차 수행
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword()));

            //인증 정보에 저장되어 있는 이메일 사용
            String email = auth.getName();

            //HTTP Request에서 헤더 정보 가져오기
            String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");
            String did = Optional.ofNullable(request.getHeader("X-Device-Id")).orElse("unknown");

            TokenResponseDto internal = authService.loginIssue(email, did, userAgent, response);
            TokenResponseDtoPublic publicDto = new TokenResponseDtoPublic(internal.getAccessToken(),
                    internal.getAccessTokenTtl(), internal.getIsPasswordSet());
            return ResponseEntity.ok(publicDto);
        } catch (BadCredentialsException e) {
            throw new ApiException(INVALID_CREDENTIALS);
        }
    }

    //앱 이용 중 access, refresh token 재발급
    //required = false -> ApiException으로 오류 처리를 직접 하기 위함
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDtoPublic> refresh(@CookieValue(value="refresh_token", required = false) String refreshToken, HttpServletResponse response){
        if(refreshToken==null || refreshToken.isBlank()){
            authService.attachRefreshCookie(response,"", Instant.now());
            throw new ApiException(MISSING_REFRESH_TOKEN);
        }
        TokenResponseDto internal = authService.refresh(refreshToken,false,response);
        TokenResponseDtoPublic dto = new TokenResponseDtoPublic(internal.getAccessToken(),
                internal.getAccessTokenTtl(), internal.getIsPasswordSet());
        return ResponseEntity.ok(dto);
    }

    //앱 시작 시 쿠키가 남아 있을 경우 자동 로그인 구현
    @PostMapping("/refresh-restart")
    public ResponseEntity<TokenResponseDtoPublic> refreshRestart(@CookieValue(value="refresh_token", required = false) String refreshToken, HttpServletResponse response){
        if(refreshToken==null || refreshToken.isBlank()){
            authService.attachRefreshCookie(response,"", Instant.now());
            throw new ApiException(MISSING_REFRESH_TOKEN);
        }
        TokenResponseDto internal = authService.refresh(refreshToken,true,response);
        TokenResponseDtoPublic dto = new TokenResponseDtoPublic(internal.getAccessToken(),
                internal.getAccessTokenTtl(), internal.getIsPasswordSet());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = "refresh_token", required = false)String refreshToken,
                                       HttpServletRequest request, HttpServletResponse response){
        //서비스를 호출해 현재 해당 기기의 모든 RefreshToken을 revoke
        String did = Optional.ofNullable(request.getHeader("X-Device-Id")).orElse("unknown");
        authService.revokeRefreshToken(refreshToken, did);
        //RefreshToken 폐기
        authService.attachRefreshCookie(response,"", Instant.now());
        return ResponseEntity.noContent().build();
    }
}
