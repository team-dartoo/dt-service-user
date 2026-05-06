package dartoo.accountService.dto.webhook;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RevenueCat이 결제 이벤트 발생 시 우리 서버로 전송하는 Webhook POST 요청 body.
 *
 * RevenueCat은 Apple/Google 결제 이벤트를 감지하면 우리 백엔드 엔드포인트로
 * 아래 형식의 JSON을 POST 한다. 이 클래스는 그 body를 Jackson이 그 형식 그대로 역직렬화할 수 있도록 매핑한다.
 */
@Getter
@NoArgsConstructor
public class RevenueCatWebhookPayload {

    private String api_version;

    //이벤트 데이터
    private Event event;

    @Getter
    @NoArgsConstructor
    public static class Event {

        /**
         * RevenueCat webhook 이벤트 자체의 고유 ID.
         * 중복 webhook 방지(idempotency)에 사용한다.
         *
         * 같은 webhook이 재전송되더라도 id는 동일하므로,
         * 이 값을 DB에 저장해두고 이미 처리한 이벤트면 무시할 수 있다.
         */
        private String id;

        /**
         * 결제 이벤트 타입.
         *
         * 처리 대상:
         *   INITIAL_PURCHASE       - 최초 결제 (SUBSCRIBE vs RENEW는 유저 상태로 판단)
         *   NON_RENEWING_PURCHASE  - 비자동갱신 구독 결제 (동일하게 유저 상태로 판단)
         *   CANCELLATION           - 구독 취소 → CANCEL 처리
         *
         * 그 외의 경우에는 구독 자동 갱신 구현 시 처리.
         */
        private String type;

        /**
         * RevenueCat 유저 식별자.
         *
         * 로그인 후 RevenueCat SDK 초기화 시 프런트엔드에서 userEmail을 app_user_id로 설정해야 함.
         * 이 값으로 UserEntity를 조회한다 (userEmail 컬럼).
         */
        private String app_user_id;

        /**
         * 결제된 상품 ID.
         * RevenueCat 대시보드 → Products에서 직접 등록한 Identifier와 정확히 일치해야 한다.
         * 예: "dartoo_premium_monthly", "dartoo_premium_yearly"
         * → RevenueCatWebhookService에서 PlanDuration으로 파싱.
         */
        private String product_id;

        /**
         * 구독 만료 시각 (Unix timestamp, 밀리초).
         *
         *  Non-Renewing Subscription 주의사항: 연장 결제 시 이전 만료일 기준이 아닌 결제 시점 기준으로 계산됨.
         *  CANCELLATION 이벤트에서는 null.
         */
        private Long expiration_at_ms;

        /**
         * Apple/Google이 발급한 결제 고유 식별자.
         * UserPlan.transactionId에 저장되며, 환불 API 호출 시 사용된다.
         *   POST /v1/subscribers/{app_user_id}/transactions/{transaction_id}/refund\
         */
        private String transaction_id;

        /**
         * 결제 스토어.
         * APP_STORE(iOS) | PLAY_STORE(Android)
         *
         * updatePlanByWebhook() 저장 시 UserPlan.store에 함께 저장됨.
         * updatePlan() CANCEL 시 스토어별 환불 분기에 사용:
         *   APP_STORE  → 앱 내 환불 불가, APPLE_REFUND_REQUIRED 예외
         *   PLAY_STORE → RevenueCat 환불 API 즉시 호출
         */
        private String store;
        /**
         * 자동갱신 구독의 취소 사유.
         * CANCELLATION 이벤트에서만 존재. 비자동갱신에서는 null.
         *
         * UNSUBSCRIBE      - 사용자가 App Store/Play Store에서 자동갱신 해제
         *                    현재 기간 만료까지 서비스 사용 가능
         * CUSTOMER_SUPPORT - 고객지원팀 환불 처리 (환불은 RevenueCat/스토어에서 이미 완료)
         */
        private String cancel_reason;
    }
}