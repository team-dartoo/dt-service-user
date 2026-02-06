package dartoo.accountService.web;

import dartoo.accountService.dto.core.PlanHistoryListResponse;
import dartoo.accountService.dto.core.PlanResponse;
import dartoo.accountService.dto.core.PlanUpdateRequest;
import dartoo.accountService.dto.core.PlanUpdateResponse;
import dartoo.accountService.service.UserPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/plan")
public class UserPlanController {
    private final UserPlanService userPlanService;

    @GetMapping
    public ResponseEntity<PlanResponse> getCurrentPlan(){
        return ResponseEntity.ok(userPlanService.getCurrentPlan());
    }

    @GetMapping("/histories")
    public ResponseEntity<PlanHistoryListResponse> getHistory(){
        return ResponseEntity.ok(userPlanService.getHistory());
    }

    @PatchMapping
    public ResponseEntity<PlanUpdateResponse> updatePlan(@Valid @RequestBody PlanUpdateRequest request){
        return ResponseEntity.ok(userPlanService.updatePlan(request));
    }
}
