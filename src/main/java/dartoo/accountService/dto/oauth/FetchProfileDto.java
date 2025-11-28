package dartoo.accountService.dto.oauth;

import dartoo.accountService.domain.Gender;
import dartoo.accountService.domain.SocialProvider;
import lombok.*;

import java.time.LocalDate;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FetchProfileDto {
    String nickname;
    String email;
    String providerId;
    SocialProvider provider;
    LocalDate birthday;
    Gender gender;
}
