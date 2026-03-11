package dartoo.accountService.dto.core;

import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.dto.core.enums.PlanAction;
import dartoo.accountService.dto.core.enums.PlanDuration;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlanUpdateRequest {
    @NotNull(message = "플랜 행위를 입력해주세요.")
    private PlanAction action;

    // 신규 구독/연장에서만 필수 필드
    private PlanType plan;
    private PlanDuration duration;
}
