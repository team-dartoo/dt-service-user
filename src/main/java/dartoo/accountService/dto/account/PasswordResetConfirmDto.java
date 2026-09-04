package dartoo.accountService.dto.account;

import lombok.Data;

@Data
public class PasswordResetConfirmDto {
    String token;
    String password;
}
