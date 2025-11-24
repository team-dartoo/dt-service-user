package dartoo.accountService.dto.oauth;

import dartoo.accountService.domain.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SocialLoginResultDto {
    UserEntity existingUserEntity;
    Boolean isNewUser;
}
