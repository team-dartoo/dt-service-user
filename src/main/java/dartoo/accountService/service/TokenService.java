package dartoo.accountService.service;

import dartoo.accountService.config.JwtConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.time.Instant;

@Service
@RequiredArgsConstructor
//Refresh Token + Access Token 발급 서비스 - 순수하게 토큰 생성만 하는 곳
public class TokenService {
    private final JwtConfig cfg;

    //Access Token 발급
    public String createAccessToken(String email, String nickname, Instant now){
        return Jwts.builder()
                .setIssuer(cfg.getIssuer())
                .setSubject(email) //변경 불가능하니까 이메일로.
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(cfg.getAccessTtlSeconds())))
                .claim("nickname",nickname)
                .signWith(cfg.getAccessKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 자동 로그인 관련 구현 방법 생각
     * RefreshToken의 수명은 2주로 정해버리고, 로그아웃 안 누르고 앱을 꺼버리면 계속 남아있게?
     * 앱은 꺼도 쿠키가 살아있으면 자동 로그인
     * 2주가 지나면 쿠키가 만료되면서 로그인 화면으로 이동
     * 근데 만료기간 (2주) 안에 다시 앱에 접속할 경우 자동으로 /refresh를 요청해 새 Access와 Refresh를 발급받는다.
     * => 앱을 재실행 할 떄와 앱 접속 중일 때의 /refresh를 통해 AT+RT발급하는 로직을 다르게
     * AuthService의 refresh 함수 안에서 분기를 시키는 것이 좋을 것 같다.
     * 컨트롤러에서 - /refresh-access는 백그라운드에서 주기적으로 호출 - /refresh-session은 앱이 다시 켜졌을 때 자동 로그인 시도용
     * 식으로 주소를 다르게 한 다음에, 이를 바탕으로 refresh함수에서 refreshToken을 다른 방식으로 발급하도록 변경
     * */

    //일반적인 Refresh Token 발급
    public String createRefreshToken(String email, String deviceId, Instant now){
        return Jwts.builder()
                .setIssuer(cfg.getIssuer())
                .setSubject(email)
                .claim("did",deviceId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(cfg.getRefreshTtlSeconds())))
                .signWith(cfg.getRefreshKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    //만료 시간을 fix해 놓는 재발급
    public String createRefreshTokenFixed(String email, String deviceId, Instant fixedExp){
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(cfg.getIssuer())
                .setSubject(email)
                .claim("did",deviceId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(fixedExp))
                .signWith(cfg.getRefreshKey(), SignatureAlgorithm.HS256)
                .compact();
    }
}