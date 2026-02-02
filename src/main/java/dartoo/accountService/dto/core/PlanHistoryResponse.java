package dartoo.accountService.dto.core;

import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PlanHistoryResponse {
    private PlanType plan;
    private PlanStatus status;
    private Instant startAt;
    private Instant expireAt;
}
