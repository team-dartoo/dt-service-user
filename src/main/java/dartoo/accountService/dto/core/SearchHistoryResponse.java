package dartoo.accountService.dto.core;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SearchHistoryResponse {
    private Long historyId;
    private String query;
    private Instant searchedAt;
}
