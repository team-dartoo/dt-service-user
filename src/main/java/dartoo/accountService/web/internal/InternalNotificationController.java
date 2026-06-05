package dartoo.accountService.web.internal;

import dartoo.accountService.dto.internal.BulkInternalNotificationRequest;
import dartoo.accountService.dto.internal.BulkNotificationResponse;
import dartoo.accountService.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {
    private final NotificationService notificationService;

    @PostMapping("/bulk")
    public ResponseEntity<BulkNotificationResponse> bulkCreate(
            @Valid @RequestBody BulkInternalNotificationRequest request) {
        BulkNotificationResponse result = notificationService.bulkCreateInternal(request.getNotifications());
        return ResponseEntity.ok(result);
    }
}
