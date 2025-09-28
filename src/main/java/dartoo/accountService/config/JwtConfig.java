package dartoo.accountService.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

@Component
@Getter
@Setter
public class JwtConfig {
    @Value("${app.jwt.issuer}")
    private String issuer;

    @Value("${app.jwt.access-secret:#{null}}")
    private String accessSecret;

    @Value("${app.jwt.refresh-secret:#{null}")
    private String refreshSecret;

    @Value("${app.jwt.refresh-pepper:#{null}")
    private String refreshPepper;

    @Value("${app.jwt.access-ttl-seconds}")
    private long accessTtlSeconds;

    @Value("${app.jwt.refresh-ttl-seconds}")
    private long refreshTtlSeconds;


    private SecretKey accessKey;
    private SecretKey refreshKey;

    @PostConstruct
    public void init() {
        String accHex = accessSecret.trim();
        String refreshHex = refreshSecret.trim();

        byte[] acc = accHex.getBytes();
        byte[] ref = refreshHex.getBytes();

        accessKey = new SecretKeySpec(acc, "HmacSHA256");
        refreshKey = new SecretKeySpec(ref, "HmacSHA256");
    }
}
/*
DB에 리프레시 토큰을 저장할 때 해시해서 저장하는데,
이 때 유출이 되어도 원문 Refresh Token을 복구하기 어렵게 만들기 위해
추가적으로 Refresh Pepper을 사용할 때가 있다.
 */
