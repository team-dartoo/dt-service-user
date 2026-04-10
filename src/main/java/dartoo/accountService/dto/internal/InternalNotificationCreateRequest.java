package dartoo.accountService.dto.internal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class InternalNotificationCreateRequest {
    @NotBlank
    private String userId;
    @NotBlank
    private String title;
    private String receptNo;
    private String corpName;
    private String corpCode;
    @NotBlank
    private String eventType;
    private List<String> summaryLines;
}
