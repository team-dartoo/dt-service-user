package dartoo.accountService.dto.oauth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthLoginTokenDto {
    String userEmail;
    String nickname;
    String accessToken;
    Long accessTokenTtl;
    Boolean isNewUser;
    Boolean isPasswordSet;
}
