package dartoo.accountService.dto.oauth;

import dartoo.accountService.domain.enums.Gender;
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
    @NotBlank //이메일은 빈칸도 막아야 하니까
    String email;
    @Size(min = 8)
    String password;
    @NotNull //LocalDate와 Enum은 @NotBlank 불가
    LocalDate birthday;
    @NotNull
    Gender gender;
}
