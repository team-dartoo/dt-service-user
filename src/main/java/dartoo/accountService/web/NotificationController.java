package dartoo.accountService.web;

import dartoo.accountService.dto.core.NotificationListResponse;
import dartoo.accountService.dto.core.NotificationResponse;
import dartoo.accountService.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationListResponse> getNotificationList(){
        return ResponseEntity.ok(notificationService.readAll());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<NotificationResponse> markNotificationAsRead(@PathVariable Long id){
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOne(@PathVariable Long id){
        notificationService.deleteOne(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll(){
        notificationService.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
