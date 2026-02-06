package dartoo.accountService.web;

import dartoo.accountService.dto.core.BookmarkCreateRequest;
import dartoo.accountService.dto.core.BookmarkListResponse;
import dartoo.accountService.dto.core.BookmarkResponse;
import dartoo.accountService.service.CorpBookmarkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/users/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {
    private final CorpBookmarkService bookmarkService;

    @GetMapping
    public ResponseEntity<BookmarkListResponse> getBookmarkList(){
        return ResponseEntity.ok(bookmarkService.listCorpBookmark());
    }

    @PostMapping
    public ResponseEntity<BookmarkResponse> addBookmark(@Valid @RequestBody BookmarkCreateRequest request){
        return ResponseEntity.ok(bookmarkService.addCorpBookmark(request));
    }

    @DeleteMapping("/{corpId}")
    public ResponseEntity<Void> deleteBookmark(@PathVariable String corpId){
        bookmarkService.deleteBookmark(corpId);
        return ResponseEntity.noContent().build();
    }
}
