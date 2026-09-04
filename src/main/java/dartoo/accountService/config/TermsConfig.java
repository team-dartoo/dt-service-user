package dartoo.accountService.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.terms")
public class TermsConfig {
    private String tosVersion;
    private String privacyVersion;
}
