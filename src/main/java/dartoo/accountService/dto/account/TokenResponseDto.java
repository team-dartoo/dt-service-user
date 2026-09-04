package dartoo.accountService.dto.account;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponseDto {
    private String accessToken;
    private Long accessTokenTtl;
    private String refreshToken;
    private Long refreshTokenTtl;
    private Boolean isPasswordSet;
    private Boolean requiresTermsConsent;
}
