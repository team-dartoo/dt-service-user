package dartoo.accountService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PreferenceSettingsDto {
    Boolean pushEnabled;
    Boolean emailEnabled;
    Integer alertDelay;
}
