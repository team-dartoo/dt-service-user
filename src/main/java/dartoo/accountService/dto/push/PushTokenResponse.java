package dartoo.accountService.dto.push;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PushTokenResponse {
    private Long userId;
    private String deviceId;
    private String fcmToken;
}
