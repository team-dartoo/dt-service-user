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
@RequestMapping("/internal/api/bookmarks")
@RequiredArgsConstructor
public class InternalBookmarkController {
    private final UserCorpBookmarkRepository userCorpBookmarkRepository;

    @GetMapping("/by-corp-code/{corpCode}")
    public ResponseEntity<List<Long>> getSubscriberIdsByCorpCode(@PathVariable String corpCode) {
        List<Long> userIds = userCorpBookmarkRepository.findAllByCorpCode(corpCode)
                .stream()
                .map(bookmark -> bookmark.getUser().getId())
                .toList();
        return ResponseEntity.ok(userIds);
    }
}
