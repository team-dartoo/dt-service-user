package dartoo.accountService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreferenceSettingsDto {
    Boolean pushEnabled;
    Boolean emailEnabled;
    Integer alertDelay;
}
