package dartoo.accountService.dto.core;

import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.dto.core.enums.PlanAction;
import dartoo.accountService.dto.core.enums.PlanDuration;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PlanUpdateResponse {
    private PlanAction action;
    private PlanType plan;
    private PlanDuration duration;
    private PlanStatus status;
    private Instant expireAt;
    private String accessToken;
}
