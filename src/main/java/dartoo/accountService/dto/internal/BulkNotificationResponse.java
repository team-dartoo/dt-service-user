package dartoo.accountService.dto.internal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BulkNotificationResponse {
    private int total;
    private int created;
    private int skipped;
}
