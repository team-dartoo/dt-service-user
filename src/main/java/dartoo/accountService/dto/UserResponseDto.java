package dartoo.accountService.dto;

import dartoo.accountService.domain.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@AllArgsConstructor
@Data
public class UserResponseDto {
    String userEmail;
    String nickname;
    LocalDate birthday;
    Gender gender;
}
