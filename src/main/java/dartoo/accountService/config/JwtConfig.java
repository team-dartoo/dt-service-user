package dartoo.accountService.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.HexFormat;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtConfig {
    private String issuer;
    private String accessSecret;
    private String refreshSecret;
    private String refreshPepper;
    private long accessTtlSeconds;
    private long refreshTtlSeconds;

    //SecretKey - JWT 서명용 키를 만들 때 쓰는 객체
    private SecretKey accessKey;
    private SecretKey refreshKey;

    @PostConstruct
    public void init() {
        String accHex = accessSecret.trim();
        String refreshHex = refreshSecret.trim();

        byte[] acc = HexFormat.of().parseHex(accHex);
        byte[] ref =  HexFormat.of().parseHex(refreshHex);

        accessKey = Keys.hmacShaKeyFor(acc);
        refreshKey = Keys.hmacShaKeyFor(ref);
    }
}
/*
DB에 리프레시 토큰을 저장할 때 해시해서 저장하는데,
이 때 유출이 되어도 원문 Refresh Token을 복구하기 어렵게 만들기 위해
추가적으로 Refresh Pepper을 사용할 때가 있다.
 */
