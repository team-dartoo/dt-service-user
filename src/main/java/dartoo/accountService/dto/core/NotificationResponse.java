package dartoo.accountService.dto.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dartoo.accountService.domain.enums.NotificationStatus;
import dartoo.accountService.domain.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class NotificationResponse {
    private Long id;

    @JsonProperty("_id")
    private String receptNo;

    private NotificationType type;
    private String corpName;
    private String corpCode;
    private String title;
    private NotificationStatus status;
    private Instant createdAt;
    private Instant readAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> summaryLines;
}
