package dartoo.accountService.web.internal;

import dartoo.accountService.repository.core.UserCorpBookmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/api/notifications/subscribers")
@RequiredArgsConstructor
public class InternalNotificationSubscriberController {

    private final UserCorpBookmarkRepository userCorpBookmarkRepository;

    @GetMapping("/by-corp-code/{corpCode}")
    public ResponseEntity<List<Long>> getPushEnabledSubscribers(@PathVariable String corpCode) {
        List<Long> userIds = userCorpBookmarkRepository.findAllByCorpCode(corpCode)
                .stream()
                .filter(b -> Boolean.TRUE.equals(
                        b.getUser().getPreference() != null
                                ? b.getUser().getPreference().getPushEnabled()
                                : false))
                .map(b -> b.getUser().getId())
                .distinct()
                .toList();
        return ResponseEntity.ok(userIds);
    }
}
