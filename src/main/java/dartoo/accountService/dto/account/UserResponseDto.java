package dartoo.accountService.dto.account;

import dartoo.accountService.domain.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseDto {
    String userEmail;
    String nickname;
    LocalDate birthday;
    Gender gender;
}
