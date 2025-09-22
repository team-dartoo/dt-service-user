package dartoo.accountService.dto;

import lombok.Data;

@Data
public class LoginRequestDto {
    String email;
    String password;
}
