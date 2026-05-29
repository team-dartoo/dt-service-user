package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPlan;
import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.dto.core.*;
import dartoo.accountService.dto.core.enums.PlanAction;
import dartoo.accountService.dto.core.enums.PlanDuration;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserPlanRepository;
import dartoo.accountService.service.revenuecat.RevenueCatRefundClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static dartoo.accountService.dto.core.enums.PlanDuration.*;
import static dartoo.accountService.error.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
/*
RevenueCat과 상관이 없는 DB에 있는 플랜에 대한 로직을 실행하고 계산하는 클래스.
만료일 계산, 환불 후 플랜 CANCEL과 REFUND에 대한 로직 처리 후 상태 변경 등의 역할을 수행한다.

실제 환불 API 호출 등은 RevenueCatWebhookService.java에서 할 예정.
 */
public class UserPlanService {

    private static final long TRIAL_PERIOD_DAYS = 7;
    //만료 며칠 전부터 구독 연장이 가능한지 -> 정책적으로 변경 가능
    //요금제마다 다르니까 구현이 복잡해져서 통일
    private static final long RENEW_WINDOW_DAYS_MONTHLY = 14;
    private static final long RENEW_WINDOW_DAYS_YEARLY = 14;

    private final UserPlanRepository userPlanRepository;
    private final UserEntityRepository userEntityRepository;
    private final TokenService tokenService;
    private final RevenueCatRefundClient revenueCatRefundClient;

    //현재 사용자의 최신 플랜 정보를 반환
    @Transactional(readOnly = true)
    public PlanResponse getCurrentPlan(){
        UserEntity user = getCurrentUser();
        return PlanResponse.builder()
                .plan(user.getPlan())
                .planExpireAt(user.getPlanExpireAt())
                .planStatus(user.getPlanStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public PlanHistoryListResponse getHistory(){
        UserEntity user = getCurrentUser();
        List<UserPlan> plans = userPlanRepository.findAllByUser_IdOrderByStartAtDesc(user.getId());
        return PlanHistoryListResponse.builder()
                .planHistoryList(plans.stream().map(p-> PlanHistoryResponse.builder()
                        .plan(p.getPlan())
                        .status(p.getStatus())
                        .startAt(p.getStartAt())
                        .expireAt(p.getExpireAt())
                        .build())
                        .collect(Collectors.toList())
                )
                .build();
    }

    //RequestDto에 적힌 액션 정보를 통해 플랜 정보를 case by case로 수정한다
    public PlanUpdateResponse updatePlan(PlanUpdateRequest request){
        UserEntity user = getCurrentUser();
        Instant now = Instant.now();
        log.info("플랜 업데이트 요청: userId={}, action={}, plan={}, duration={}",
                user.getId(), request.getAction(), request.getPlan(), request.getDuration());

        if(request.getDuration()==TRIAL) {
            //무료 체험 검증 로직 추가 1 - 이미 무료 체험을 사용한 경우
            boolean usedTrial = userPlanRepository.existsByUser_IdAndDuration(user.getId(), PlanDuration.TRIAL);
            if (usedTrial) throw new ApiException(TRIAL_ALREADY_USED);
            //무료 체험 검증 로직 추가 2 - 이미 유료 결제를 한 경우
            boolean hasPaidHistory = userPlanRepository.existsByUser_IdAndDurationIn(
                    user.getId(), List.of(PlanDuration.MONTHLY, PlanDuration.YEARLY)
            );
            if (hasPaidHistory) throw new ApiException(TRIAL_NOT_ALLOWED_FOR_EXISTING_CUSTOMER);
        }

        // null 체크를 switch 앞에 추가
        if (request.getAction() == null) {
            throw new ApiException(INVALID_UPDATE_PLAN_ACTION);
        }

        switch (request.getAction()){
            case SUBSCRIBE, RENEW -> {
                //requestDto 검증 로직
                //FREE -> PREMIUM만 존재하기 때문에, FREE로 바꾸는건 CANCEL로 처리
                if (request.getPlan() != PlanType.PREMIUM) {
                    throw new ApiException(INVALID_PLAN_UPDATE_REQUEST);
                }
                //DURATION 정보는 필수
                if (request.getDuration() == null) {
                    throw new ApiException(INVALID_PLAN_DURATION);
                }

                //이미 프리미엄 요금제를 쓰고 있는 경우에는 SUBSCRIBE가 아니라 연장을 해야함.
                if (request.getAction() == PlanAction.SUBSCRIBE && user.getPlan() == PlanType.PREMIUM
                        && user.getPlanExpireAt() != null && user.getPlanExpireAt().isAfter(now)) {
                    throw new ApiException(ALREADY_SUBSCRIBED);
                }

                //기존의 구독이 존재한다면 그 구독이 언제 끝나는지를 파악
                Instant currentExpireAt = now; //현재 사이클 만료일
                if(user.getPlan()==PlanType.PREMIUM && user.getPlanExpireAt()!=null && user.getPlanExpireAt().isAfter(now)){
                    UserPlan currentPlan = userPlanRepository
                            .findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                                    user.getId(), now, now, List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED)
                            )
                            .orElseThrow(() -> new ApiException(INVALID_RENEW_REQUEST));
                    currentExpireAt = currentPlan.getExpireAt();
                }

                //구독 갱신(RENEW) 시의 검증 로직
                validateRenewWindowAndUniqueness(user,request.getAction(), now,currentExpireAt);

                //새로운 플랜 만료일을 계산
                Instant newExpireAt = calculateNewExpireAt(currentExpireAt, request.getDuration());

                //새로운 요금제 정보 저장
                //RENEW의 경우에는 startAt이 미래 시간이기 때문에, 현재 정보를 검색할 때 영향이 없음
                userPlanRepository.save(UserPlan.builder()
                                .user(user)
                                .plan(request.getPlan())
                                .duration(request.getDuration())
                                .status(PlanStatus.ACTIVE)
                                .startAt(currentExpireAt) //만료되는 시점부터 다시 시작
                                .expireAt(newExpireAt)
                                .build());
                //user 테이블에 업데이트
                user.updatePlan(PlanType.PREMIUM,PlanStatus.ACTIVE,newExpireAt);

                //새로운 정보 기반 토큰 발급
                String newAccessToken = tokenService.createAccessToken(user,now);

                log.info("플랜 업데이트 완료: userId={}, action={}, oldExpireAt={}, newExpireAt={}, status={}",
                        user.getId(), request.getAction(), currentExpireAt, newExpireAt, user.getPlanStatus());

                return PlanUpdateResponse.builder()
                        .action(request.getAction())
                        .plan(PlanType.PREMIUM)
                        .duration(request.getDuration())
                        .status(user.getPlanStatus())
                        .expireAt(user.getPlanExpireAt())
                        .accessToken(newAccessToken)
                        .build();
            }
            case CANCEL -> {
                //쿼리 한번만 호출하게 변경
                List<UserPlan> cancelablePlans = userPlanRepository
                        .findAllByUser_IdAndExpireAtAfterAndStatus(user.getId(), now, PlanStatus.ACTIVE);

                if (cancelablePlans.isEmpty()) {
                    throw new ApiException(PLAN_TO_CANCEL_NOT_FOUND);
                }

                // Apple 결제건이 포함된 경우 앱 내 환불 불가 → 즉시 예외
                // (현재 플랜 포함 모든 Apple 결제건은 Apple에 직접 환불 요청해야 함)
                // Apple이 환불 승인 시 RevenueCat이 CANCELLATION Webhook 자동 발송 → 그때 DB 처리
                boolean hasApplePlan = cancelablePlans.stream()
                        .anyMatch(plan -> "APP_STORE".equals(plan.getStore()));
                if (hasApplePlan) {
                    throw new ApiException(APPLE_REFUND_REQUIRED);
                }

                // Google Play만 도달
                // 현재 플랜: CANCELLED, 미래 연장분: 환불 API 호출 후 REFUNDED
                AtomicInteger refundedCount = new AtomicInteger();
                cancelablePlans.forEach(plan -> {
                    if (plan.getStartAt().isBefore(now)) {
                        // 현재 진행 중: 만료일까지 서비스 이용 가능 → CANCELLED
                        plan.changeStatus(PlanStatus.CANCELLED);
                    } else {
                        // 미시작 연장분: 환불 API 호출 성공해야 REFUNDED 마킹
                        // 실패 시 ApiException이 전파되어 오류 응답
                        revenueCatRefundClient.refund(user.getUserEmail(), plan.getTransactionId());
                        plan.changeStatus(PlanStatus.REFUNDED);
                        refundedCount.getAndIncrement();
                    }
                });

                user.updatePlan(PlanType.PREMIUM, PlanStatus.CANCELLED, user.getPlanExpireAt());

                String newAccessToken = tokenService.createAccessToken(user, now);

                log.info("플랜 취소 완료: userId={}, 취소된 플랜 수={}, 환불된 플랜 수={}",
                        user.getId(), cancelablePlans.size() - refundedCount.get(), refundedCount.get());

                return PlanUpdateResponse.builder()
                        .action(PlanAction.CANCEL)
                        .plan(user.getPlan())
                        .duration(null)
                        .status(user.getPlanStatus())
                        .expireAt(user.getPlanExpireAt())
                        .accessToken(newAccessToken)
                        .build();
            }
            default -> throw new ApiException(INVALID_UPDATE_PLAN_ACTION);
        }
    }

    private Instant calculateNewExpireAt(Instant currentExpireAt, PlanDuration duration) {
        return switch (duration) {
            case MONTHLY -> currentExpireAt.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
            case YEARLY  -> currentExpireAt.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
            case TRIAL   -> currentExpireAt.plus(TRIAL_PERIOD_DAYS, ChronoUnit.DAYS);
        };
    }

    private void validateRenewWindowAndUniqueness(UserEntity user,
                                                  PlanAction action,
                                                  Instant now,
                                                  Instant currentExpireAt){

        // SUBSCRIBE는 신규 결제로 보고, RENEW만 정책 검증 수행
        if (action!=PlanAction.RENEW) {
            return;
        }

        //현재 플랜이 없으면 RENEW가 아니라 SUBSCRIBE를 해야하니까
        if(user.getPlan()==PlanType.FREE){
            throw new ApiException(INVALID_RENEW_REQUEST);
        }

        //currentExpireAt 방어
        if (currentExpireAt==null||!currentExpireAt.isAfter(now)) {
            throw new ApiException(INVALID_RENEW_REQUEST);
        }

        //연장 중복 금지: currentExpireAt 이후 시작하는 ACTIVE row가 이미 있으면 거절
        boolean hasFutureActivePlan = userPlanRepository
                .existsByUser_IdAndStartAtGreaterThanEqualAndStatus(
                        user.getId(), currentExpireAt, PlanStatus.ACTIVE
                );
        if (hasFutureActivePlan) {
            throw new ApiException(ALREADY_RENEWED);
        }

        //duration별 연장 가능 윈도우 체크
        PlanDuration currentDuration = userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                user.getId(),now,now,List.of(PlanStatus.ACTIVE,PlanStatus.CANCELLED))
                .orElseThrow(()-> new ApiException(INVALID_RENEW_REQUEST)).getDuration();

        long windowDays = switch (currentDuration) {
            case MONTHLY -> RENEW_WINDOW_DAYS_MONTHLY;
            case YEARLY -> RENEW_WINDOW_DAYS_YEARLY;
            case TRIAL -> -1; // 정책: 일단 TRIAL은 연장 불가로 처리 (무료 체험 다 끝나고 결제하는게 자연스러우니까)
        };

        if (windowDays < 0) {
            throw new ApiException(INVALID_RENEW_DURATION);
        }

        Instant renewAllowedFrom = currentExpireAt.minus(windowDays, ChronoUnit.DAYS);
        if (now.isBefore(renewAllowedFrom)) {
            // 너무 이르게 연장하려는 경우
            throw new ApiException(RENEW_NOT_ALLOWED_YET);
        }

    }

    private String getSessionEmail(){
        //SecurityContextHolder.getContext().getAuthentication()에
        //JWT 토큰 정보가 저장되도록 AuthenticationManger을설정해야 한다.
        //->SecurityConfig.java 참조
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    public UserEntity getCurrentUser(){
        return userEntityRepository.findByUserEmail(getSessionEmail())
                .orElseThrow(()->new ApiException(USER_NOT_FOUND));
    }

    /*
     * RevenueCat Webhook 업데이트 시 플랜 DB 업데이트 관련 메서드.
     * RevenueCat의 경우에는 유저를 외부에서 주입받아, SecurityContextHolder에 들어가 있는 사용자 정보가 없다.
     * 외부에 의해 호출되는 메서드기 때문에, JWT 토큰 재발급이 없다.
     * 따라서, 프런트에서 결제 완료 직후 앱에서 플랜 상태를 즉시 반영하려면,
     * RevenueCat SDK의 결제 완료 콜백 후 바로 새 액세스 토큰을 받아와야 한다.
     *
     * Webhook CANCEL은 Apple이 환불을 승인한 후 RevenueCat이 발송하는 이벤트.
     * Apple이 이미 환불을 처리했으므로 환불 API 호출 없이 DB 상태만 업데이트한다.
     *
     * 기존 updatePlan은 사용자가 플랜에 대한 TRIAL 요청 시에,
     * 관리자가 특정 userId에 대해 수동으로 플랜을 업데이트 해야할 시에 사용하도록 놔두면 됨.
     */
    public void updatePlanByWebhook(UserEntity user, PlanAction action, PlanDuration duration,
                                    Instant webhookExpireAt, String transactionId, String store){
        Instant now = Instant.now();
        log.info("[Webhook] updatePlanByWebhook: userId={}, action={}, duration={}", user.getId(), action, duration);

        switch (action){
            case SUBSCRIBE -> {
                // 현재 활성 플랜이 없는 신규 구독
                userPlanRepository.save(UserPlan.builder()
                        .user(user)
                        .plan(PlanType.PREMIUM)
                        .duration(duration)
                        .status(PlanStatus.ACTIVE)
                        .startAt(now)
                        .expireAt(webhookExpireAt) //webhook에서 계산해 준 예상 만료일
                        .transactionId(transactionId)
                        .store(store)
                        .build());
                user.updatePlan(PlanType.PREMIUM,PlanStatus.ACTIVE,webhookExpireAt);
                log.info("[Webhook] SUBSCRIBE 완료: userId={}, expireAt={}", user.getId(), webhookExpireAt);
            }
            case RENEW -> {
                // 현재 활성/취소된 플랜 조회 (연장의 기준)
                UserPlan currentPlan = userPlanRepository
                        .findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                                user.getId(),now,now,List.of(PlanStatus.ACTIVE,PlanStatus.CANCELLED))
                        .orElseThrow(()-> new ApiException(INVALID_UPDATE_PLAN_ACTION));

                validateRenewWindowAndUniqueness(user,action,now,currentPlan.getExpireAt());

                // startAt: 현재 플랜 만료일 = 새 플랜 시작일 (구독 연속성 보장)
                // expireAt: webhookExpireAt 미사용, 현재 만료일 기준으로 직접 계산
                Instant newStartAt = currentPlan.getExpireAt();
                Instant newExpireAt = calculateNewExpireAt(newStartAt, duration);

                userPlanRepository.save(UserPlan.builder()
                        .user(user)
                        .plan(PlanType.PREMIUM)
                        .duration(duration)
                        .status(PlanStatus.ACTIVE)
                        .startAt(newStartAt)
                        .expireAt(newExpireAt)
                        .transactionId(transactionId)
                        .store(store)
                        .build());
                user.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, newExpireAt);
                log.info("[Webhook] RENEW 완료: userId={}, expireAt={}", user.getId(), newExpireAt);
            }
            case CANCEL -> {
                // Webhook CANCEL = Apple이 환불 승인 후 RevenueCat이 발송한 이벤트
                // Apple이 이미 환불을 처리했으므로 환불 API 호출 없이 DB 상태만 업데이트

                //쿼리는 한번만 호출하고 forEach문을 통해 현재와 미래 ACTIVE 플랜 모두 호출 해 상태 변경
                //만료되지 않은 ACTIVE 플랜 전체 조회
                List<UserPlan> cancelablePlans = userPlanRepository
                        .findAllByUser_IdAndExpireAtAfterAndStatus(user.getId(),now,PlanStatus.ACTIVE);
                AtomicInteger refundedPlans = new AtomicInteger();
                cancelablePlans.forEach(plan -> {
                    if(plan.getStartAt().isBefore(now)){
                        plan.changeStatus(PlanStatus.CANCELLED);
                    }
                    else{
                        plan.changeStatus(PlanStatus.REFUNDED);
                        refundedPlans.getAndIncrement();
                    }
                });

                user.updatePlan(PlanType.PREMIUM,PlanStatus.CANCELLED,user.getPlanExpireAt());
                log.info("[Webhook] CANCEL 완료: userId={}, 취소된 플랜 수={}, 환불된 플랜 수={}",
                        user.getId(), cancelablePlans.size()-refundedPlans.get(),refundedPlans.get());
            }
            case EXPIRE -> {
                UserPlan expiredPlan = userPlanRepository
                        .findTopByUser_IdAndTransactionIdAndStatusInOrderByExpireAtDesc(
                                user.getId(),
                                transactionId,
                                List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED)
                        )
                        .orElseThrow(() -> new ApiException(INVALID_UPDATE_PLAN_ACTION));

                expiredPlan.changeStatus(PlanStatus.EXPIRED);

                // 유저의 최종 premium 만료 시각도 이미 지났다면 FREE로 전환
                // 미래 연장분이 있으면 user.planExpireAt이 아직 미래이므로 그대로 둔다.
                if (user.getPlanExpireAt() != null && !user.getPlanExpireAt().isAfter(now)) {
                    user.updatePlan(PlanType.FREE, PlanStatus.EXPIRED, null);
                }

                log.info("[Webhook] EXPIRE 완료: userId={}, transactionId={}, expireAt={}",
                        user.getId(), transactionId, webhookExpireAt);
            }
            case AUTO_RENEW -> {
                if (webhookExpireAt == null){
                    log.error("[Webhook] AUTO_RENEW 이벤트에 expiration_at_ms 없음: userId={}", user.getId());
                    throw new ApiException(INVALID_UPDATE_PLAN_ACTION);
                }
                UserPlan currentPlan = userPlanRepository
                        .findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                                user.getId(),now,now,List.of(PlanStatus.ACTIVE,PlanStatus.CANCELLED)
                        ).orElseThrow(()-> new ApiException(INVALID_UPDATE_PLAN_ACTION));

                Instant newStartAt = currentPlan.getExpireAt();

                userPlanRepository.save(UserPlan.builder()
                        .user(user)
                        .plan(PlanType.PREMIUM)
                        .duration(duration)
                        .status(PlanStatus.ACTIVE)
                        .startAt(newStartAt)
                        .expireAt(webhookExpireAt)
                        .transactionId(transactionId)
                        .store(store)
                        .build());

                user.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, webhookExpireAt);
                log.info("[Webhook] AUTO_RENEW 완료: userId={}, newStartAt={}, expireAt={}",
                        user.getId(), newStartAt, webhookExpireAt);
            }
        }
    }

    //아직 시작되지 않은 미래 연장분의 transactionId 목록 반환.
    //RevenueCatWebhookService에서 CANCEL 이벤트 처리 시 환불 대상 조회에 사용.
    public List<String> getFuturePlanTransactionIds(Long userId) {
        Instant now = Instant.now();
        return userPlanRepository
                .findAllByUser_IdAndExpireAtAfterAndStatus(userId, now, PlanStatus.ACTIVE)
                .stream()
                .filter(plan -> plan.getStartAt().isAfter(now))
                .map(UserPlan::getTransactionId)
                .filter(Objects::nonNull)
                .toList();
    }
}
/*
자동 결제는 구현하기 어려워서
일단 매번 선결제하는 모델을 기반으로 만들었습니다.
(연장은 만료가 다가오기 직전 1회만 허용)
-> RevenueCatWebhookPayload 구조 덕분에 자동 결제 구현 난이도가 낮아져서,
일단 수동 결제부터 구현 뒤, 구현할 예정.

CANCEL -> 현재 프리미엄 사용 상태를 취소로 표시하고, 만료일까지 사용 가능함.
현재 사이클의 경우에는 이미 결제한 기간까지 사용하고,
미리 RENEW를 통해 구매한 미래의 구독 사이클에 대해서는 전체 취소 후 환불로 구현함.
 */

/*
TRIAL 부여
  → 기존 updatePlan() 그대로 사용
  → 앱에서 직접 POST /api/users/plan 호출
  → JWT 재발급까지 기존 로직 그대로

MONTHLY / YEARLY 결제
  → RevenueCat Webhook으로만 들어옴
  → updatePlanByWebhook() 처리
  → JWT는 앱 SDK 콜백에서 별도 갱신
 */