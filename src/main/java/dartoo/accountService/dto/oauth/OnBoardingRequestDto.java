package dartoo.accountService.dto.oauth;

import dartoo.accountService.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class OnBoardingRequestDto {
    @NotBlank
    String nickname;
    @NotNull
    String email;
    @Size(min = 8)
    String password;
    @NotBlank
    LocalDate birthday;
    @NotBlank
    Gender gender;
}
