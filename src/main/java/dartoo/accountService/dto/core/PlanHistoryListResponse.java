package dartoo.accountService.dto.core;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PlanHistoryListResponse {
    private List<PlanHistoryResponse> planHistoryList;
}
