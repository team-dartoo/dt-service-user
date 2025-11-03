package dartoo.accountService.dto;

import dartoo.accountService.domain.Gender;
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
