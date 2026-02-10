package dartoo.accountService.web;

import dartoo.accountService.dto.core.SearchHistoryCreateRequest;
import dartoo.accountService.dto.core.SearchHistoryListResponse;
import dartoo.accountService.dto.core.SearchHistoryResponse;
import dartoo.accountService.service.SearchHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/search-histories")
@RequiredArgsConstructor
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    @GetMapping
    public ResponseEntity<SearchHistoryListResponse> getSearchHistoryList(@RequestParam(defaultValue = "30") int limit){
        return ResponseEntity.ok(searchHistoryService.readHistory(limit));
    }

    @PostMapping
    public ResponseEntity<SearchHistoryResponse> addSearchHistory(@Valid @RequestBody SearchHistoryCreateRequest request){
        return ResponseEntity.ok(searchHistoryService.addHistory(request));
    }

    @DeleteMapping("/{historyId}")
    public ResponseEntity<Void> deleteOne(@PathVariable Long historyId){
        searchHistoryService.deleteOne(historyId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll(){
        searchHistoryService.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
