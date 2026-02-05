package dartoo.accountService.service.scheduler;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPlan;
import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//User 테이블과 UserPlan 테이블에 있는 정보를 자동으로 업데이트
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanExpireScheduler {
    private final UserPlanRepository userPlanRepository;
    private final UserEntityRepository userEntityRepository;

    @Scheduled(cron = "0 */1 * * * *")
    @Transactional
    public void expirePlans(){
        Instant now = Instant.now();

        //1. 만료된 플랜들을 DB에서 찾아 만료로 변경
        List<UserPlan> expiredPlans = userPlanRepository.findAllByExpireAtBeforeAndStatusIn(
                now, List.of(PlanStatus.ACTIVE,PlanStatus.CANCELLED)
        );

        expiredPlans.forEach(p->p.changeStatus(PlanStatus.EXPIRED));

        //2. 만료된 플랜이 가장 최신 플랜인 사용자를 찾아
        // 선결제를 해둔 상황이면 해당 버전으로 DB를 업데이트,
        // 그렇지 않은 경우에는 FREE로 변경
        // 매번 사용자별로 쿼리를 보내는 N+1 문제를 막기 위해 처음에 한번 쭉 땡겨온 다음에 for문을 돌면서 변경
        List<UserEntity> candidates = userEntityRepository.findAllByPlanAndPlanExpireAtBefore(PlanType.PREMIUM,now);
        int renewed = 0; int free = 0;
        if (!candidates.isEmpty()) {
            //모든 후보들의 userId를 뽑는다.
            List<Long> userIds = candidates.stream().map(UserEntity::getId).toList();

            //모든 후보들에 대한 ACTIVE 플랜 조회.
            List<UserPlan> activePlans = userPlanRepository.findAllActivePlansForUsers(userIds, now, PlanStatus.ACTIVE);

            //이후 사용자별로, 가장 최신 플랜을 매핑한다.
            Map<Long, UserPlan> activePlanByUserId = activePlans.stream()
                    .collect(Collectors.toMap(
                            p -> p.getUser().getId(),
                            p -> p,
                            (a, b) -> Comparator.comparing(UserPlan::getExpireAt).compare(a, b) >= 0 ? a : b
                    ));

            //for문은 쿼리 없이 맵만 보고 업데이트
            for (UserEntity user : candidates) {
                UserPlan active = activePlanByUserId.get(user.getId());

                if (active != null) {
                    user.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, active.getExpireAt());
                    renewed++;
                } else {
                    user.updatePlan(PlanType.FREE, PlanStatus.EXPIRED, null);
                    free++;
                }
            }
            log.info("{}명의 사용자 구독 갱신, 만료된 플랜 사용자 {}명 FREE로 변경", renewed, free);
        }
    }
}

