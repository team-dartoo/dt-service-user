package dartoo.accountService.dto.core;

import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import lombok.Data;

import java.time.Instant;

//GET /api/users/plan
@Data
public class PlanResponse {
    private PlanType plan;
    private Instant planExpireAt;
    private PlanStatus planStatus;
}
