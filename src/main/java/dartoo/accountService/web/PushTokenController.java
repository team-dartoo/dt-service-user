package dartoo.accountService.web;

import dartoo.accountService.dto.push.PushTokenRegisterRequest;
import dartoo.accountService.dto.push.PushTokenResponse;
import dartoo.accountService.service.PushTokenProxyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/users/notifications/push")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenProxyService pushTokenProxyService;

    @PostMapping("/tokens")
    public ResponseEntity<PushTokenResponse> registerPushToken(
            @Valid @RequestBody PushTokenRegisterRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        PushTokenResponse response = pushTokenProxyService.registerPushToken(
                email, request.getDeviceId(), request.getFcmToken());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tokens/{deviceId}")
    public ResponseEntity<PushTokenResponse> getPushToken(@PathVariable String deviceId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<PushTokenResponse> response = pushTokenProxyService.getPushToken(email, deviceId);
        return response.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/tokens/{deviceId}")
    public ResponseEntity<Void> deletePushToken(@PathVariable String deviceId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        pushTokenProxyService.deactivatePushToken(email, deviceId);
        return ResponseEntity.noContent().build();
    }
}
