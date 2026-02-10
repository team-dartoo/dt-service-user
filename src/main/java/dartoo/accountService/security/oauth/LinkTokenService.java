package dartoo.accountService.security.oauth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.sql.Date;
import java.time.Instant;
import java.util.HexFormat;

@Service
@Getter
@Setter
@ConfigurationProperties(prefix = "app.oauth")
public class LinkTokenService {

    private String linkSecret;
    private long linkTtlSeconds;
    private String linkCookieName;
    private SecretKey linkKey;
    private String issuer;

    @PostConstruct
    private void setUpLinkKey(){
        String linkHex = linkSecret.trim();
        byte[] link = HexFormat.of().parseHex(linkHex);
        linkKey = Keys.hmacShaKeyFor(link);
    }

    //링크 토큰 발급 후 쿠키 형태로 저장
    public void issueLinkCookie(String userEmail, HttpServletResponse response){
        Instant now = Instant.now();

        String linkJwt = Jwts.builder()
                .setSubject(userEmail)
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(linkTtlSeconds)))
                .signWith(linkKey, SignatureAlgorithm.HS256)
                .compact();

        ResponseCookie cookie = ResponseCookie.from(linkCookieName,linkJwt)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(linkTtlSeconds)
                .build();

        response.addHeader("Set-Cookie",cookie.toString());
    }

    public String parseLinkCookie(String cookie) {
        return Jwts.parserBuilder().setSigningKey(linkKey).setAllowedClockSkewSeconds(30).build().parseClaimsJws(cookie).getBody().getSubject();
    }
}
