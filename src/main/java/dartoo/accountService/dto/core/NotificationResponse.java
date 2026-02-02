package dartoo.accountService.dto.core;

import dartoo.accountService.domain.enums.NotificationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private String title;
    private String content;
    private NotificationStatus status;
    private Instant createdAt;
    private Instant readAt;
}
