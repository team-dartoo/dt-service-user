package dartoo.accountService.service.revenuecat;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPlan;
import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.dto.core.enums.PlanAction;
import dartoo.accountService.dto.core.enums.PlanDuration;
import dartoo.accountService.dto.webhook.RevenueCatWebhookPayload;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.service.UserPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RevenueCatWebhookServiceTest {
    @Mock
    private UserEntityRepository userEntityRepository;
    @Mock private UserPlanService userPlanService;
    @Mock private RevenueCatRefundClient revenueCatRefundClient;
    @Mock private ProcessedWebhookEventService processedWebhookEventService;

    @InjectMocks
    private RevenueCatWebhookService revenueCatWebhookService;

    private static final String VALID_SECRET = "test-webhook-secret";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        //테스트용 @Value 시크릿 값 강제 주입
        ReflectionTestUtils.setField(revenueCatWebhookService, "webhookSecret", VALID_SECRET);
        // 기본적으로 중복 이벤트가 아닌 것으로 설정
        // 2개의 클래스만 이 stub을 안써서 lenient 처리
        lenient().when(processedWebhookEventService.markAsFirstDelivery(any())).thenReturn(true);
    }

    // ========== Secret 검증 ==========

    @Test
    @DisplayName("Secret 불일치 시 INVALID_WEBHOOK_SECRET 예외, 이후 로직 실행 안 됨")
    void handleWebhook_invalidSecret() {
        //given
        RevenueCatWebhookPayload payload = buildPayload(
                "INITIAL_PURCHASE", "user@test.com", "dartoo_premium_monthly",
                NOW.plus(30, ChronoUnit.DAYS).toEpochMilli(), "tx_001", "PLAY_STORE");

        //when & then
        assertThatThrownBy(() -> revenueCatWebhookService.handleWebhook("wrong-secret", payload))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", INVALID_WEBHOOK_SECRET);

        then(userEntityRepository).shouldHaveNoInteractions();
    }

    // ========== 이벤트 타입 필터링 ==========

    @Test
    @DisplayName("처리 대상 아닌 이벤트(RENEWAL) 수신 시 DB 접근 없이 조기 반환")
    void handleWebhook_ignoredEventType_renewal() {
        //given
        RevenueCatWebhookPayload payload = buildPayload(
                "INVALID_TYPE", "user@test.com", "dartoo_premium_monthly",
                NOW.plus(30, ChronoUnit.DAYS).toEpochMilli(), "tx_001", "PLAY_STORE");

        //when
        revenueCatWebhookService.handleWebhook(VALID_SECRET, payload);

        //then
        then(userEntityRepository).shouldHaveNoInteractions();
        then(userPlanService).shouldHaveNoInteractions();
    }

    // ========== 중복 이벤트 처리 ==========

    @Test
    @DisplayName("중복 이벤트 수신 시 비즈니스 로직 실행 없이 조기 반환")
    void handleWebhook_duplicateEvent() {
        //given
        given(processedWebhookEventService.markAsFirstDelivery(any())).willReturn(false);

        RevenueCatWebhookPayload payload = buildPayload(
                "INITIAL_PURCHASE", "user@test.com", "dartoo_premium_monthly",
                NOW.plus(30, ChronoUnit.DAYS).toEpochMilli(), "tx_001", "PLAY_STORE");

        //when
        revenueCatWebhookService.handleWebhook(VALID_SECRET, payload);

        //then
        then(userEntityRepository).shouldHaveNoInteractions();
        then(userPlanService).shouldHaveNoInteractions();
    }

    // ========== SUBSCRIBE 처리 ==========

    @Test
    @DisplayName("활성 플랜 없는 유저의 INITIAL_PURCHASE → SUBSCRIBE 액션으로 처리")
    void handleWebhook_initialPurchase_noActivePlan_subscribe() {
        //given
        UserEntity user = buildUser(NOW.minus(1, ChronoUnit.DAYS));
        Instant webhookExpireAt = NOW.plus(30, ChronoUnit.DAYS);

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));

        RevenueCatWebhookPayload payload = buildPayload(
                "INITIAL_PURCHASE", "user@test.com", "dartoo_premium_monthly",
                webhookExpireAt.toEpochMilli(), "tx_001", "PLAY_STORE");

        //when
        revenueCatWebhookService.handleWebhook(VALID_SECRET, payload);

        //then - 다음을 인수로 가지는 메서드가 호출되어야 함
        then(userPlanService).should().updatePlanByWebhook(
                eq(user),
                eq(PlanAction.SUBSCRIBE),
                eq(PlanDuration.MONTHLY),
                argThat(expireAt -> Math.abs(expireAt.toEpochMilli() - webhookExpireAt.toEpochMilli()) < 1000),
                eq("tx_001"),
                eq("PLAY_STORE")
        );
        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

    // ========== RENEW 처리 ==========

    @Test
    @DisplayName("활성 플랜 있는 유저의 NON_RENEWING_PURCHASE → RENEW 액션으로 처리")
    void handleWebhook_nonRenewingPurchase_hasActivePlan_renew() {
        //given
        UserEntity user = buildUser(NOW.plus(7, ChronoUnit.DAYS));

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));

        RevenueCatWebhookPayload payload = buildPayload(
                "NON_RENEWING_PURCHASE", "user@test.com", "dartoo_premium_monthly",
                NOW.plus(37, ChronoUnit.DAYS).toEpochMilli(), "tx_002", "PLAY_STORE");

        //when
        revenueCatWebhookService.handleWebhook(VALID_SECRET, payload);

        //then - 다음을 인수로 가지는 메서드가 호출되어야 함
        then(userPlanService).should().updatePlanByWebhook(
                eq(user), eq(PlanAction.RENEW), eq(PlanDuration.MONTHLY),
                any(), eq("tx_002"), eq("PLAY_STORE"));
    }

    @Test
    @DisplayName("updatePlanByWebhook 호출 시, RENEW 실패(RENEW_NOT_ALLOWED_YET) 예외 발생 시 Google 결제건 자동 환불")
    void handleWebhook_renew_validationFail_autoRefund() {
        //given
        UserEntity user = buildUser(NOW.plus(30, ChronoUnit.DAYS));

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));
        willThrow(new ApiException(RENEW_NOT_ALLOWED_YET))
                .given(userPlanService)
                .updatePlanByWebhook(any(), eq(PlanAction.RENEW), any(), any(), any(), any());

        RevenueCatWebhookPayload payload = buildPayload(
                "NON_RENEWING_PURCHASE", "user@test.com", "dartoo_premium_monthly",
                NOW.plus(60, ChronoUnit.DAYS).toEpochMilli(), "tx_003", "PLAY_STORE");

        //when & then
        assertThatThrownBy(() -> revenueCatWebhookService.handleWebhook(VALID_SECRET, payload))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", RENEW_NOT_ALLOWED_YET);

        then(revenueCatRefundClient).should().refund("user@test.com", "tx_003");
    }

    @Test
    @DisplayName("RENEW 실패 시 Apple 결제건은 환불 API skip")
    void handleWebhook_renew_validationFail_appleSkipRefund() {
        //given
        UserEntity user = buildUser(NOW.plus(30, ChronoUnit.DAYS));

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));
        willThrow(new ApiException(RENEW_NOT_ALLOWED_YET))
                .given(userPlanService)
                .updatePlanByWebhook(any(), eq(PlanAction.RENEW), any(), any(), any(), any());

        RevenueCatWebhookPayload payload = buildPayload(
                "NON_RENEWING_PURCHASE", "user@test.com", "dartoo_premium_monthly",
                NOW.plus(60, ChronoUnit.DAYS).toEpochMilli(), "tx_003", "APP_STORE");

        //when & then
        assertThatThrownBy(() -> revenueCatWebhookService.handleWebhook(VALID_SECRET, payload))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", RENEW_NOT_ALLOWED_YET);

        // 상태 검증: Apple은 환불 API 호출 없음
        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

    // ========== CANCEL 처리 ==========

    @Test
    @DisplayName("CANCELLATION 이벤트 → CANCEL 액션으로 updatePlanByWebhook 호출, 환불 API 없음")
    void handleWebhook_cancellation_dbUpdateOnly() {
        //given
        // Webhook CANCEL = Apple이 이미 환불 처리한 이벤트 → 환불 API 호출 없이 DB만 업데이트
        UserEntity user = buildUser(NOW.plus(14, ChronoUnit.DAYS));

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));

        RevenueCatWebhookPayload payload = buildPayload(
                "CANCELLATION", "user@test.com", null, null, "tx_004", "APP_STORE");

        //when
        revenueCatWebhookService.handleWebhook(VALID_SECRET, payload);

        //then
        then(userPlanService).should().updatePlanByWebhook(
                eq(user), eq(PlanAction.CANCEL), isNull(), isNull(), eq("tx_004"), eq("APP_STORE"));
        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

    // ========== EXPIRE 처리 ==========

    @Test
    @DisplayName("EXPIRATION 이벤트 → EXPIRE 액션으로 updatePlanByWebhook 호출")
    void handleWebhook_expiration_expireAction() {
        //given
        UserEntity user = buildUser(NOW.minus(1, ChronoUnit.DAYS));

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));

        RevenueCatWebhookPayload payload = buildPayload(
                "EXPIRATION", "user@test.com", null, null, "tx_005", "PLAY_STORE");

        //when
        revenueCatWebhookService.handleWebhook(VALID_SECRET, payload);

        //then
        then(userPlanService).should().updatePlanByWebhook(
                eq(user), eq(PlanAction.EXPIRE), isNull(), isNull(), eq("tx_005"), eq("PLAY_STORE"));
        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

    // ========== 유저 조회 / product_id 파싱 ==========

    @Test
    @DisplayName("app_user_id에 해당 유저 없음 → USER_NOT_FOUND 예외")
    void handleWebhook_userNotFound() {
        //given
        given(userEntityRepository.findByUserEmail("unknown@test.com")).willReturn(Optional.empty());

        RevenueCatWebhookPayload payload = buildPayload(
                "INITIAL_PURCHASE", "unknown@test.com", "dartoo_premium_monthly",
                NOW.plus(30, ChronoUnit.DAYS).toEpochMilli(), "tx_001", "PLAY_STORE");

        //when & then
        assertThatThrownBy(() -> revenueCatWebhookService.handleWebhook(VALID_SECRET, payload))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @Test
    @DisplayName("등록되지 않은 product_id → INVALID_PRODUCT_ID 예외")
    void handleWebhook_unknownProductId() {
        //given
        UserEntity user = buildUser(null);
        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));

        RevenueCatWebhookPayload payload = buildPayload(
                "INITIAL_PURCHASE", "user@test.com", "unknown_product",
                NOW.plus(30, ChronoUnit.DAYS).toEpochMilli(), "tx_001", "PLAY_STORE");

        //when & then
        assertThatThrownBy(() -> revenueCatWebhookService.handleWebhook(VALID_SECRET, payload))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", INVALID_PRODUCT_ID);
    }

    // ========== RENEWAL (AUTO_RENEW) 처리 ==========

    @Test
    @DisplayName("RENEWAL 이벤트 수신 시 AUTO_RENEW 액션으로 updatePlanByWebhook 호출")
    void handleWebhook_renewal_autoRenewAction() {
        //given
        UserEntity user = buildUser(NOW.plus(3, ChronoUnit.DAYS));
        Instant webhookExpireAt = NOW.plus(33, ChronoUnit.DAYS);

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));

        RevenueCatWebhookPayload payload = buildPayload(
                "RENEWAL", "user@test.com", "dartoo_premium_monthly_auto",
                webhookExpireAt.toEpochMilli(), "tx_auto_001", "PLAY_STORE");

        //when
        revenueCatWebhookService.handleWebhook(VALID_SECRET, payload);

        //then
        then(userPlanService).should().updatePlanByWebhook(
                eq(user),
                eq(PlanAction.AUTO_RENEW),
                eq(PlanDuration.MONTHLY),
                argThat(expireAt -> Math.abs(expireAt.toEpochMilli() - webhookExpireAt.toEpochMilli()) < 1000),
                eq("tx_auto_001"),
                eq("PLAY_STORE")
        );
        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("RENEWAL 처리 실패 시 Google 결제건 자동 환불")
    void handleWebhook_renewal_failure_googleRefund() {
        //given
        UserEntity user = buildUser(NOW.plus(3, ChronoUnit.DAYS));

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));
        willThrow(new ApiException(INVALID_UPDATE_PLAN_ACTION))
                .given(userPlanService)
                .updatePlanByWebhook(any(), eq(PlanAction.AUTO_RENEW), any(), any(), any(), any());

        RevenueCatWebhookPayload payload = buildPayload(
                "RENEWAL", "user@test.com", "dartoo_premium_monthly_auto",
                NOW.plus(33, ChronoUnit.DAYS).toEpochMilli(), "tx_auto_001", "PLAY_STORE");

        //when & then
        assertThatThrownBy(() -> revenueCatWebhookService.handleWebhook(VALID_SECRET, payload))
                .isInstanceOf(ApiException.class);

        then(revenueCatRefundClient).should().refund("user@test.com", "tx_auto_001");
    }

    @Test
    @DisplayName("RENEWAL 처리 실패 시 Apple 결제건은 환불 API skip")
    void handleWebhook_renewal_failure_appleSkipRefund() {
        //given
        UserEntity user = buildUser(NOW.plus(3, ChronoUnit.DAYS));

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));
        willThrow(new ApiException(INVALID_UPDATE_PLAN_ACTION))
                .given(userPlanService)
                .updatePlanByWebhook(any(), eq(PlanAction.AUTO_RENEW), any(), any(), any(), any());

        RevenueCatWebhookPayload payload = buildPayload(
                "RENEWAL", "user@test.com", "dartoo_premium_yearly_auto",
                NOW.plus(365, ChronoUnit.DAYS).toEpochMilli(), "tx_auto_002", "APP_STORE");

        //when & then
        assertThatThrownBy(() -> revenueCatWebhookService.handleWebhook(VALID_SECRET, payload))
                .isInstanceOf(ApiException.class);

        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

// ========== 자동갱신 CANCELLATION (cancel_reason) 처리 ==========

    @Test
    @DisplayName("cancel_reason=UNSUBSCRIBE CANCELLATION → CANCEL 액션으로 처리, 환불 API 없음")
    void handleWebhook_cancellation_unsubscribe_dbUpdateOnly() {
        //given
        UserEntity user = buildUser(NOW.plus(14, ChronoUnit.DAYS));

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));

        RevenueCatWebhookPayload payload = buildPayload(
                "CANCELLATION", "user@test.com", null,
                null, "tx_auto_003", "PLAY_STORE", "UNSUBSCRIBE");

        //when
        revenueCatWebhookService.handleWebhook(VALID_SECRET, payload);

        //then
        then(userPlanService).should().updatePlanByWebhook(
                eq(user), eq(PlanAction.CANCEL), isNull(), isNull(),
                eq("tx_auto_003"), eq("PLAY_STORE"));
        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("cancel_reason=CUSTOMER_SUPPORT CANCELLATION → CANCEL 액션으로 처리, 환불 API 없음")
    void handleWebhook_cancellation_customerSupport_dbUpdateOnly() {
        //given
        // CUSTOMER_SUPPORT = 고객지원 환불 처리. 환불은 RevenueCat/스토어에서 이미 완료.
        UserEntity user = buildUser(NOW.plus(14, ChronoUnit.DAYS));

        given(userEntityRepository.findByUserEmail("user@test.com")).willReturn(Optional.of(user));

        RevenueCatWebhookPayload payload = buildPayload(
                "CANCELLATION", "user@test.com", null,
                null, "tx_auto_004", "APP_STORE", "CUSTOMER_SUPPORT");

        //when
        revenueCatWebhookService.handleWebhook(VALID_SECRET, payload);

        //then
        then(userPlanService).should().updatePlanByWebhook(
                eq(user), eq(PlanAction.CANCEL), isNull(), isNull(),
                eq("tx_auto_004"), eq("APP_STORE"));
        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

    // 헬퍼 메서드들
    private UserEntity buildUser(Instant planExpireAt) {
        UserEntity user = UserEntity.builder()
                .userEmail("user@test.com")
                .plan(PlanType.FREE)
                .planStatus(PlanStatus.ACTIVE)
                .build();
        if (planExpireAt != null) {
            user.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, planExpireAt);
        }
        return user;
    }

    private RevenueCatWebhookPayload buildPayload(String type, String appUserId, String productId,
                                                  Long expirationAtMs, String transactionId,
                                                  String store) {
        RevenueCatWebhookPayload payload = new RevenueCatWebhookPayload();
        RevenueCatWebhookPayload.Event event = new RevenueCatWebhookPayload.Event();

        ReflectionTestUtils.setField(event, "id", "event-" + transactionId);
        ReflectionTestUtils.setField(event, "type", type);
        ReflectionTestUtils.setField(event, "app_user_id", appUserId);
        ReflectionTestUtils.setField(event, "product_id", productId);
        ReflectionTestUtils.setField(event, "expiration_at_ms", expirationAtMs);
        ReflectionTestUtils.setField(event, "transaction_id", transactionId);
        ReflectionTestUtils.setField(event, "store", store);
        ReflectionTestUtils.setField(payload, "event", event);

        return payload;
    }

    //새로운 필드 추가에 따른 새로운 메서드 오버로드
    private RevenueCatWebhookPayload buildPayload(String type, String appUserId, String productId,
                                                  Long expirationAtMs, String transactionId,
                                                  String store, String cancelReason) {
        RevenueCatWebhookPayload payload = buildPayload(type, appUserId, productId,
                expirationAtMs, transactionId, store);
        ReflectionTestUtils.setField(payload.getEvent(), "cancel_reason", cancelReason);
        return payload;
    }
}