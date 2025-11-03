package dartoo.accountService.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreferenceSettingsDto {
    @NotNull
    Boolean pushEnabled;
    @NotNull
    Boolean emailEnabled;
    @NotNull
    Integer alertDelay;
}
