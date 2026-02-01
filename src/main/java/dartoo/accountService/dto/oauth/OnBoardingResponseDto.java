package dartoo.accountService.dto.oauth;

import dartoo.accountService.domain.enums.Gender;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class OnBoardingResponseDto {
    String userEmail;
    Boolean isPasswordSet;
    String nickname;
    LocalDate birthday;
    Gender gender;
}
