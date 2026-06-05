package dartoo.accountService.web;

import dartoo.accountService.dto.webhook.RevenueCatWebhookPayload;
import dartoo.accountService.service.revenuecat.RevenueCatWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/webhooks")
public class RevenueCatWebhookController {

    private final RevenueCatWebhookService revenueCatWebhookService;

    /*
    RevenueCat으로부터 결제 이벤트를 수신한다.
    RevenueCatWebhookPayload를 통해 RequestBody를 담은 뒤, 검증 로직은 서비스에 위임한다.
    RevenueCat은 2xx 응답 시 성공으로 처리하고, 그 외 응답을 받으면 실패로 처리해 재시도한다.
     */
    @PostMapping("/revenuecat")
    public ResponseEntity<Void> handleWebhook(@RequestHeader ("Authorization") String auth,
                                              @RequestBody RevenueCatWebhookPayload payload){
        revenueCatWebhookService.handleWebhook(auth, payload);
        return ResponseEntity.ok().build();
    }
}
