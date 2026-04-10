package dartoo.accountService.dto.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkInternalNotificationRequest {
    @Valid
    @NotEmpty
    private List<InternalNotificationCreateRequest> notifications;
}
