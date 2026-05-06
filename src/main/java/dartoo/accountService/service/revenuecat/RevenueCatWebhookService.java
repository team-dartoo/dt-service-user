package dartoo.accountService.service.revenuecat;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.dto.core.enums.PlanAction;
import dartoo.accountService.dto.core.enums.PlanDuration;
import dartoo.accountService.dto.webhook.RevenueCatWebhookPayload;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.service.UserPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

import static dartoo.accountService.error.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
//RevenueCat에서 Webhook 방식으로 보낸 DTO RequestBody를 분석해,
//UserPlanService에 어떤 동작을 해야할 지 알려주는 클래스
public class RevenueCatWebhookService {
    private final RevenueCatRefundClient revenueCatRefundClient;
    private final UserEntityRepository userEntityRepository;
    private final UserPlanService userPlanService;
    private final ProcessedWebhookEventService processedWebhookEventService;

    @Value("${revenuecat.webhook.secret}")
    private String webhookSecret;

    public void handleWebhook(String authHeader, RevenueCatWebhookPayload payload){

        //형식에 맞는지 체크
        validateSecret(authHeader);
        RevenueCatWebhookPayload.Event event = payload.getEvent();
        log.info("[Webhook] 이벤트 수신 : type={}, app_user_id={}, cancel_reason={}", event.getType(), event.getApp_user_id(), event.getCancel_reason());

        if (!isSupportedEventType(event.getType())) {
            log.info("[Webhook] 이벤트 무시 : type={}", event.getType());
            return;
        }

        //중복 webhook 방지 - https://www.revenuecat.com/docs/integrations/webhooks#handle-duplicate-events
        // RevenueCat은 네트워크 문제나 응답 신호 2xx 미수신 등의 이유로 동일한 webhook을 재전송할 수 있다.
        // 따라서 event.id를 별도 테이블에 기록해두고,
        // 이미 처리한 동일 event.id가 다시 들어오면 비즈니스 로직을 재실행하지 않고 즉시 종료한다.
        // 중복 이벤트는 "실패"가 아니라 "이미 처리 완료된 요청"으로 간주하므로 조용히 return 한다.
        if(!processedWebhookEventService.markAsFirstDelivery(event)){
            return;
        }

        //사용자 정보 확인
        UserEntity user = userEntityRepository.findByUserEmail(event.getApp_user_id())
                        .orElseThrow(()->new ApiException(USER_NOT_FOUND));

        //확인 후 UserPlanService.java에 들어갈 수 있도록 RequestBody 파싱
        PlanAction action = parsePlanAction(event.getType(), user);
        PlanDuration duration = (action == PlanAction.CANCEL || action == PlanAction.EXPIRE)
                ? null : parsePlanDuration(event.getProduct_id());
        Instant webhookExpiredAt = event.getExpiration_at_ms() != null
                ? Instant.ofEpochMilli(event.getExpiration_at_ms())
                : null;

        try {
            userPlanService.updatePlanByWebhook(
                    user,
                    action,
                    duration,
                    webhookExpiredAt,
                    event.getTransaction_id(),
                    event.getStore()
            );
        } catch (ApiException e) {
            log.error("[Webhook] 플랜 업데이트 실패: userId={}, transactionId={}, error={}",
                    user.getId(), event.getTransaction_id(), e.getMessage());

            // 구매/연장 처리 실패일 때만 환불 시도
            if (action == PlanAction.SUBSCRIBE || action == PlanAction.RENEW || action == PlanAction.AUTO_RENEW) {
                refundHandler(event.getApp_user_id(), event.getTransaction_id(), event.getStore());
            }

            throw e;
        }
        log.info("[Webhook] 처리 완료: userId={}, action={}", user.getId(), action);
    }


    /**
     * RevenueCat 환불 API 호출.
     * APP_STORE(Apple)는 환불 API 미지원 → skip.
     * Apple 환불은 사용자가 직접 Apple에 요청 → 승인 시 CANCELLATION Webhook 자동 발송됨.
     * PLAY_STORE(Google)는 RevenueCat 환불 API 즉시 호출.
     */
    private void refundHandler(String appUserId, String transactionId, String store){
        if(store.equals("APP_STORE")){
            log.info("[Refund] Apple 결제건 환불 API skip: transactionId={}", transactionId);
            return;
        }
        log.info("[Refund] Google 환불 요청: appUserId={}, transactionId={}", appUserId, transactionId);
        revenueCatRefundClient.refund(appUserId, transactionId);
    }

    //Secret 값이 .env에 있는 Secret값과 일치하는지 확인
    private void validateSecret(String authHeader){
        if(!webhookSecret.equals(authHeader)){
            log.warn("[Webhook] Invalid Secret: {}, Request rejected", authHeader);
            throw new ApiException(INVALID_WEBHOOK_SECRET);
        }
    }

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "INITIAL_PURCHASE",
            "NON_RENEWING_PURCHASE",
            "CANCELLATION",
            "EXPIRATION",
            "RENEWAL"
    );

    //우리가 지원하는 결제 Action인지 확인
    private boolean isSupportedEventType(String eventType) {
        return SUPPORTED_EVENT_TYPES.contains(eventType);
    }

    /**
     * event.type을 파싱해 UserPlanService에 들어갈 PlanAction ENUM으로 변환한다.
     * 만료 후 재구독 시에도 NON_RENEWING_PURCHASE가 발송되기 때문에, 이를 위해 UserEntity도 인수로 받는다.
     * (유저의 planExpireAt이 현재 시각 이후인지로 판단함)
     */
    private PlanAction parsePlanAction(String eventType, UserEntity user) {
        if ("CANCELLATION".equals(eventType)) {
            return PlanAction.CANCEL;
        }
        if ("EXPIRATION".equals(eventType)) {
            return PlanAction.EXPIRE;
        }
        if("RENEWAL".equals(eventType)){
            return PlanAction.AUTO_RENEW;
        }

        // INITIAL_PURCHASE, NON_RENEWING_PURCHASE
        // event.type만으로 신규/연장 구분 불가 → 유저 상태로 판단
        boolean hasActivePlan = user.getPlanExpireAt() != null
                && user.getPlanExpireAt().isAfter(Instant.now());
        return hasActivePlan ? PlanAction.RENEW : PlanAction.SUBSCRIBE;
    }

    //product_id는 RevenueCat 대시보드 → Products → Identifier와 정확히 일치해야 한다.
    private static final Set<String> MONTHLY_PRODUCT_IDS = Set.of(
            "dartoo_premium_monthly",
            // 자동갱신 전환 시: "dartoo_premium_monthly_auto" 추가
            "dartoo_premium_monthly_auto"
    );

    private static final Set<String> YEARLY_PRODUCT_IDS = Set.of(
            "dartoo_premium_yearly",
            // 자동갱신 전환 시: "dartoo_premium_yearly_auto" 추가
            "dartoo_premium_yearly_auto"
    );

    /**
     * product_id → PlanDuration 변환.
     * product_id는 RevenueCat 대시보드 → Products → Identifier와 정확히 일치해야 한다.
     *
     * TRIAL은 RevenueCat 결제 상품이 아니므로 Webhook으로 수신될 수 없음.
     */
    private PlanDuration parsePlanDuration(String productId) {
        if (MONTHLY_PRODUCT_IDS.contains(productId)) return PlanDuration.MONTHLY;
        if (YEARLY_PRODUCT_IDS.contains(productId)) return PlanDuration.YEARLY;

        log.warn("[Webhook] 등록되지 않은 product_id: {}", productId);
        throw new ApiException(INVALID_PRODUCT_ID);
    }
}

/**
 * 처리 흐름:
 *   1. Secret 검증
 *   2. 처리 대상 이벤트 타입 필터링 (무시 이벤트 조기 반환)
 *   3. app_user_id로 UserEntity 조회
 *   4. PlanAction 결정
 *      - INITIAL_PURCHASE / NON_RENEWING_PURCHASE: event.type만으로 SUBSCRIBE vs RENEW 구분 불가
 *        → planExpireAt.isAfter(now)로 유저 상태 직접 확인
 *      - CANCELLATION: CANCEL 처리
 *   5. product_id로 PlanDuration 파싱
 *   6. UserPlanService.updatePlanByWebhook() 호출
 *   7. ApiException 발생 시 해당 결제건 자동 환불
 *      결제는 됐는데 서버 처리 불가 → RevenueCat에 환불 요청 (Google Play만 가능)
 *
 * [Webhook CANCEL 처리 방식]
 *   Webhook CANCEL = Apple이 환불을 승인한 후 RevenueCat이 자동으로 발송하는 이벤트.
 *   Apple이 이미 환불을 처리했으므로 환불 API 호출 없이 DB 상태만 업데이트한다.
 *   Apple이 환불 승인
 *         ↓
 * RevenueCat → CANCELLATION Webhook 발송
 *         ↓
 * handleWebhook() 진입
 *         ↓
 * parsePlanAction("CANCELLATION", user) → PlanAction.CANCEL
 *         ↓
 * parsePlanDuration(event.getProduct_id())
 *         ↓
 * parsePlanDuration() skip (null)
 *         ↓
 * webhookExpireAt = null (CANCELLATION은 expiration_at_ms 없음)
 *         ↓
 * updatePlanByWebhook(user, CANCEL, null, null, transactionId, store)
 *         ↓
 * CANCEL case 진입
 *         ↓
 * findAllByUser_IdAndExpireAtAfterAndStatus() 로 만료 안된 ACTIVE 플랜 전체 조회
 *         ↓
 * cancelablePlans.forEach()
 *   startAt < now  → CANCELLED
 *   startAt > now  → REFUNDED (Apple이 이미 환불 처리한 미래 연장분)
 *         ↓
 * user.updatePlan(PREMIUM, CANCELLED, user.getPlanExpireAt())
 *         ↓
 * 200 OK → RevenueCat
 *
 * [사용자가 앱에서 취소 버튼을 누르는 경우]
 *   updatePlan() CANCEL이 처리. (이 클래스와 무관)
 *   - Apple 결제: 앱 내 환불 불가 → 즉시 APPLE_REFUND_REQUIRED 예외 반환
 *   - Google 결제: RevenueCat 환불 API 호출 → 성공 시 REFUNDED, 실패 시 오류 응답
 *
 * [비자동갱신 vs 자동갱신 처리 비교]
 *   비자동갱신 (NON_RENEWING_PURCHASE):
 *     - SUBSCRIBE/RENEW: parsePlanAction()에서 planExpireAt.isAfter(now)로 구분
 *     - RENEW: validateRenewWindowAndUniqueness() + calculateNewExpireAt() 적용
 *     - CANCEL: 사용자가 앱 내 취소 → updatePlan() 처리
 *              Apple 환불 승인 → CANCELLATION Webhook → DB 상태만 업데이트
 *
 *   자동갱신 (RENEWAL / CANCELLATION with cancel_reason):
 *     - AUTO_RENEW: RENEWAL 이벤트 → 유저 상태 확인 없이 AUTO_RENEW로 바로 매핑
 *                  validateRenewWindowAndUniqueness() 없음 (갱신 시점은 RevenueCat 결정)
 *                  expiration_at_ms 그대로 사용 (calculateNewExpireAt() 없음)
 *     - CANCEL: CANCELLATION (cancel_reason=UNSUBSCRIBE) → 기존 CANCEL 로직 그대로 적용
 *              현재 플랜 → CANCELLED (만료일까지 사용), 미래 플랜 → REFUNDED
 *              cancel_reason=CUSTOMER_SUPPORT도 동일하게 DB 상태만 업데이트
 *              (환불은 RevenueCat/스토어에서 이미 완료)
 */