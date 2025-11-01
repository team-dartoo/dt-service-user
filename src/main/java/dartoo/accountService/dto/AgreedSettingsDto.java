package dartoo.accountService.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgreedSettingsDto {
    @NotNull
    Boolean tosAgreed;
    String tosVersion;

    @NotNull
    Boolean privacyAgreed;
    String privacyVersion;

    @NotNull
    Boolean marketingAgreed;
}
