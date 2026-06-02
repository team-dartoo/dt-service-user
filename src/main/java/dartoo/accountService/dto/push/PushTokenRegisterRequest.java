package dartoo.accountService.dto.push;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PushTokenRegisterRequest {
    @NotBlank(message = "deviceId는 필수입니다.")
    private String deviceId;
    @NotBlank(message = "fcmToken은 필수입니다.")
    private String fcmToken;
}
