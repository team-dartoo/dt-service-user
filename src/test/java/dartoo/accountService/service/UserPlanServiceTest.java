package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPlan;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.dto.core.*;
import dartoo.accountService.dto.core.enums.PlanAction;
import dartoo.accountService.dto.core.enums.PlanDuration;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserPlanRepository;
import dartoo.accountService.service.revenuecat.RevenueCatRefundClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserPlanServiceTest {

    @Mock
    UserPlanRepository userPlanRepository;
    @Mock
    UserEntityRepository userEntityRepository;
    @Mock
    TokenService tokenService;

    @InjectMocks
    UserPlanService userPlanService;

    @Mock private RevenueCatRefundClient revenueCatRefundClient;

    private UserEntity testUser;
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id(1L)
                .userEmail("test@test.com")
                .password("encodedPassword")
                .nickname("테스터")
                .role(Role.USER)
                .gender(Gender.MALE)
                .birthday(LocalDate.of(2000, 11, 16))
                .build();

        mockSecurityContext("test@test.com");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ========== getCurrentPlan 테스트 ==========

    @Test
    @DisplayName("현재 플랜 정보 조회 성공")
    void getCurrentPlan_success() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, now.plus(30, ChronoUnit.DAYS));

        PlanResponse response = userPlanService.getCurrentPlan();

        assertThat(response.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(response.getPlanStatus()).isEqualTo(PlanStatus.ACTIVE);
    }

    @Test
    @DisplayName("현재 플랜 조회 실패 - 사용자를 찾을 수 없음")
    void getCurrentPlan_userNotFound() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userPlanService.getCurrentPlan())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    // ========== getHistory 테스트 ==========

    @Test
    @DisplayName("플랜 이력 조회 성공")
    void getHistory_success() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        UserPlan plan1 = UserPlan.builder()
                .user(testUser).plan(PlanType.PREMIUM).duration(PlanDuration.MONTHLY)
                .status(PlanStatus.ACTIVE).startAt(now).expireAt(now.plus(30, ChronoUnit.DAYS))
                .build();

        given(userPlanRepository.findAllByUser_IdOrderByStartAtDesc(1L)).willReturn(List.of(plan1));

        PlanHistoryListResponse response = userPlanService.getHistory();

        assertThat(response.getPlanHistoryList()).hasSize(1);
        assertThat(response.getPlanHistoryList().get(0).getPlan()).isEqualTo(PlanType.PREMIUM);
    }

    @Test
    @DisplayName("플랜 이력 조회 실패 - 사용자를 찾을 수 없음")
    void getHistory_userNotFound() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userPlanService.getHistory())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    // ========== updatePlan - 공통 실패 ==========

    @Test
    @DisplayName("플랜 업데이트 실패 - 잘못된 액션 (null)")
    void updatePlan_invalidAction() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        PlanUpdateRequest request = PlanUpdateRequest.builder().action(null).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", INVALID_UPDATE_PLAN_ACTION);
    }

    @Test
    @DisplayName("플랜 업데이트 실패 - 사용자를 찾을 수 없음")
    void updatePlan_userNotFound() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.empty());

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE).plan(PlanType.PREMIUM).duration(PlanDuration.MONTHLY).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    // ========== updatePlan - SUBSCRIBE 테스트 ==========

    @Test
    @DisplayName("SUBSCRIBE 성공 - MONTHLY")
    void updatePlan_subscribe_monthly_success() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(tokenService.createAccessToken(any(UserEntity.class), any(Instant.class))).willReturn("newAccessToken");

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE).plan(PlanType.PREMIUM).duration(PlanDuration.MONTHLY).build();

        PlanUpdateResponse response = userPlanService.updatePlan(request);

        assertThat(response.getAction()).isEqualTo(PlanAction.SUBSCRIBE);
        assertThat(response.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(response.getDuration()).isEqualTo(PlanDuration.MONTHLY);
        assertThat(response.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(response.getAccessToken()).isEqualTo("newAccessToken");
        then(userPlanRepository).should().save(any(UserPlan.class));
    }

    @Test
    @DisplayName("SUBSCRIBE 성공 - YEARLY")
    void updatePlan_subscribe_yearly_success() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(tokenService.createAccessToken(any(UserEntity.class), any(Instant.class))).willReturn("newAccessToken");

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE).plan(PlanType.PREMIUM).duration(PlanDuration.YEARLY).build();

        PlanUpdateResponse response = userPlanService.updatePlan(request);

        assertThat(response.getDuration()).isEqualTo(PlanDuration.YEARLY);
        assertThat(response.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        then(userPlanRepository).should().save(any(UserPlan.class));
    }

    @Test
    @DisplayName("SUBSCRIBE 성공 - TRIAL")
    void updatePlan_subscribe_trial_success() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userPlanRepository.existsByUser_IdAndDuration(1L, PlanDuration.TRIAL)).willReturn(false);
        given(userPlanRepository.existsByUser_IdAndDurationIn(1L, List.of(PlanDuration.MONTHLY, PlanDuration.YEARLY))).willReturn(false);
        given(tokenService.createAccessToken(any(UserEntity.class), any(Instant.class))).willReturn("newAccessToken");

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE).plan(PlanType.PREMIUM).duration(PlanDuration.TRIAL).build();

        PlanUpdateResponse response = userPlanService.updatePlan(request);

        assertThat(response.getDuration()).isEqualTo(PlanDuration.TRIAL);
        assertThat(response.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        then(userPlanRepository).should().save(any(UserPlan.class));
    }

    @Test
    @DisplayName("SUBSCRIBE 실패 - plan이 PREMIUM이 아님")
    void updatePlan_subscribe_invalidPlan() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE).plan(PlanType.FREE).duration(PlanDuration.MONTHLY).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", INVALID_PLAN_UPDATE_REQUEST);
    }

    @Test
    @DisplayName("SUBSCRIBE 실패 - duration이 null")
    void updatePlan_subscribe_nullDuration() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE).plan(PlanType.PREMIUM).duration(null).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", INVALID_PLAN_DURATION);
    }

    @Test
    @DisplayName("SUBSCRIBE 실패 - 이미 구독 중")
    void updatePlan_subscribe_alreadySubscribed() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, now.plus(30, ChronoUnit.DAYS));

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE).plan(PlanType.PREMIUM).duration(PlanDuration.MONTHLY).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ALREADY_SUBSCRIBED);
    }

    @Test
    @DisplayName("SUBSCRIBE 실패 - 무료 체험 이미 사용함")
    void updatePlan_subscribe_trialAlreadyUsed() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userPlanRepository.existsByUser_IdAndDuration(1L, PlanDuration.TRIAL)).willReturn(true);

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE).plan(PlanType.PREMIUM).duration(PlanDuration.TRIAL).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", TRIAL_ALREADY_USED);
    }

    @Test
    @DisplayName("SUBSCRIBE 실패 - 유료 결제 이력 있는 경우 무료 체험 불가")
    void updatePlan_subscribe_trialNotAllowedForExistingCustomer() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userPlanRepository.existsByUser_IdAndDuration(1L, PlanDuration.TRIAL)).willReturn(false);
        given(userPlanRepository.existsByUser_IdAndDurationIn(1L, List.of(PlanDuration.MONTHLY, PlanDuration.YEARLY))).willReturn(true);

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE).plan(PlanType.PREMIUM).duration(PlanDuration.TRIAL).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", TRIAL_NOT_ALLOWED_FOR_EXISTING_CUSTOMER);
    }

    // ========== updatePlan - RENEW 테스트 ==========

    @Test
    @DisplayName("RENEW 성공 - MONTHLY 구독 만료 14일 이내 연장")
    void updatePlan_renew_monthly_success() {
        // 현재 구독이 7일 후 만료 → 14일 이내이므로 연장 가능
        Instant expireAt = now.plus(7, ChronoUnit.DAYS);
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(23, ChronoUnit.DAYS), expireAt);
        given(userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                eq(1L), any(), any(), eq(List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED))
        )).willReturn(Optional.of(currentPlan));

        given(userPlanRepository.existsByUser_IdAndStartAtGreaterThanEqualAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)
        )).willReturn(false);

        given(tokenService.createAccessToken(any(), any())).willReturn("newAccessToken");

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.RENEW).plan(PlanType.PREMIUM).duration(PlanDuration.MONTHLY).build();

        PlanUpdateResponse response = userPlanService.updatePlan(request);

        assertThat(response.getAction()).isEqualTo(PlanAction.RENEW);
        assertThat(response.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        then(userPlanRepository).should().save(any(UserPlan.class));
    }

    @Test
    @DisplayName("RENEW 성공 - YEARLY 구독 만료 14일 이내 연장")
    void updatePlan_renew_yearly_success() {
        // 현재 구독이 20일 후 만료 → 30일 이내이므로 연장 가능
        Instant expireAt = now.plus(7, ChronoUnit.DAYS);
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.YEARLY, now.minus(345, ChronoUnit.DAYS), expireAt);
        given(userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                eq(1L), any(), any(), eq(List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED))
        )).willReturn(Optional.of(currentPlan));

        given(userPlanRepository.existsByUser_IdAndStartAtGreaterThanEqualAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)
        )).willReturn(false);

        given(tokenService.createAccessToken(any(), any())).willReturn("newAccessToken");

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.RENEW).plan(PlanType.PREMIUM).duration(PlanDuration.YEARLY).build();

        PlanUpdateResponse response = userPlanService.updatePlan(request);

        assertThat(response.getAction()).isEqualTo(PlanAction.RENEW);
        assertThat(response.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        then(userPlanRepository).should().save(any(UserPlan.class));
    }

    @Test
    @DisplayName("RENEW 실패 - FREE 유저는 RENEW 불가")
    void updatePlan_renew_freeUser() {
        // testUser의 기본 plan은 FREE
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.RENEW).plan(PlanType.PREMIUM).duration(PlanDuration.MONTHLY).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", INVALID_RENEW_REQUEST);
    }

    @Test
    @DisplayName("RENEW 실패 - 이미 미래 연장분이 존재함")
    void updatePlan_renew_alreadyRenewed() {
        Instant expireAt = now.plus(7, ChronoUnit.DAYS);
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(23, ChronoUnit.DAYS), expireAt);
        given(userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                eq(1L), any(), any(), eq(List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED))
        )).willReturn(Optional.of(currentPlan));

        // 이미 연장된 미래 플랜 존재
        given(userPlanRepository.existsByUser_IdAndStartAtGreaterThanEqualAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)
        )).willReturn(true);

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.RENEW).plan(PlanType.PREMIUM).duration(PlanDuration.MONTHLY).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ALREADY_RENEWED);
    }

    @Test
    @DisplayName("RENEW 실패 - 갱신 가능 기간 전 (14일 이전)")
    void updatePlan_renew_tooEarly_monthly() {
        // 만료까지 20일 남음 → MONTHLY 갱신 가능 기간(14일 이내)에 해당 안 됨
        Instant expireAt = now.plus(20, ChronoUnit.DAYS);
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(10, ChronoUnit.DAYS), expireAt);
        given(userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                eq(1L), any(), any(), eq(List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED))
        )).willReturn(Optional.of(currentPlan));

        given(userPlanRepository.existsByUser_IdAndStartAtGreaterThanEqualAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)
        )).willReturn(false);

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.RENEW).plan(PlanType.PREMIUM).duration(PlanDuration.MONTHLY).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", RENEW_NOT_ALLOWED_YET);
    }

    @Test
    @DisplayName("RENEW 실패 - TRIAL은 연장 불가")
    void updatePlan_renew_trialNotRenewable() {
        Instant expireAt = now.plus(3, ChronoUnit.DAYS);
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.TRIAL, now.minus(4, ChronoUnit.DAYS), expireAt);
        given(userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                eq(1L), any(), any(), eq(List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED))
        )).willReturn(Optional.of(currentPlan));

        given(userPlanRepository.existsByUser_IdAndStartAtGreaterThanEqualAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)
        )).willReturn(false);

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.RENEW).plan(PlanType.PREMIUM).duration(PlanDuration.MONTHLY).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", INVALID_RENEW_DURATION);
    }

    // ========== updatePlan - CANCEL 로직 변경으로 테스트 메서드 변경 ==========

    @Test
    @DisplayName("CANCEL 성공 - Google Play 현재 플랜만 있는 경우")
    void updatePlan_cancel_googlePlay_currentPlanOnly() {
        //given
        // 현재 진행 중인 플랜 1개만 존재 → 미래 연장분 없으므로 환불 API 호출 없음
        Instant expireAt = now.plus(14, ChronoUnit.DAYS);
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(16, ChronoUnit.DAYS),
                expireAt, "tx_001", "PLAY_STORE");
        given(userPlanRepository.findAllByUser_IdAndExpireAtAfterAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)))
                .willReturn(List.of(currentPlan));

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.CANCEL).build();

        //when
        PlanUpdateResponse response = userPlanService.updatePlan(request);

        //then
        // 상태 검증
        assertThat(response.getAction()).isEqualTo(PlanAction.CANCEL);
        assertThat(response.getStatus()).isEqualTo(PlanStatus.CANCELLED);
        // 상호작용 검증: 현재 플랜만 있으면 환불 API 호출 없음
        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CANCEL 성공 - Google Play 미래 연장분 환불 API 호출 후 REFUNDED")
    void updatePlan_cancel_googlePlay_withFuturePlan_refundSuccess() {
        //given
        // 현재 플랜(startAt < now) + 미래 연장분(startAt > now) 2개 존재
        // 미래 연장분은 환불 API 호출 후 REFUNDED로 마킹
        Instant currentExpireAt = now.plus(7, ChronoUnit.DAYS);
        Instant futureExpireAt = currentExpireAt.plus(30, ChronoUnit.DAYS);
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, futureExpireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(23, ChronoUnit.DAYS),
                currentExpireAt, "tx_001", "PLAY_STORE");
        UserPlan futurePlan = buildActivePlan(PlanDuration.MONTHLY, currentExpireAt,
                futureExpireAt, "tx_002", "PLAY_STORE");

        given(userPlanRepository.findAllByUser_IdAndExpireAtAfterAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)))
                .willReturn(List.of(currentPlan, futurePlan));

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.CANCEL).build();

        //when
        PlanUpdateResponse response = userPlanService.updatePlan(request);

        //then
        // 상태 검증
        assertThat(response.getAction()).isEqualTo(PlanAction.CANCEL);
        // 상호작용 검증: 미래 연장분 환불 API 호출
        then(revenueCatRefundClient).should().refund("test@test.com", "tx_002");
    }

    @Test
    @DisplayName("CANCEL 실패 - Apple 결제건 포함 시 APPLE_REFUND_REQUIRED 예외")
    void updatePlan_cancel_applePlan_throwsAppleRefundRequired() {
        //given
        // Apple 결제건은 RevenueCat 환불 API 미지원
        // → 즉시 예외 반환, 사용자가 Apple에 직접 환불 요청해야 함
        Instant expireAt = now.plus(14, ChronoUnit.DAYS);
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan applePlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(16, ChronoUnit.DAYS),
                expireAt, "tx_001", "APP_STORE");
        given(userPlanRepository.findAllByUser_IdAndExpireAtAfterAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)))
                .willReturn(List.of(applePlan));

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.CANCEL).build();

        //when & then
        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", APPLE_REFUND_REQUIRED);

        // 상태 검증: Apple은 환불 API 호출 없음
        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("CANCEL 실패 - 취소할 플랜 없음")
    void updatePlan_cancel_noPlan() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userPlanRepository.findAllByUser_IdAndExpireAtAfterAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)))
                .willReturn(List.of());

        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.CANCEL).build();

        //when & then
        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", PLAN_TO_CANCEL_NOT_FOUND);
    }

// ========== updatePlanByWebhook ==========

    @Test
    @DisplayName("updatePlanByWebhook - SUBSCRIBE 성공")
    void updatePlanByWebhook_subscribe_success() {
        //given
        // SecurityContextHolder 없이 user를 직접 주입받는 Webhook 전용 메서드
        Instant expireAt = now.plus(30, ChronoUnit.DAYS);

        //when
        userPlanService.updatePlanByWebhook(testUser, PlanAction.SUBSCRIBE, PlanDuration.MONTHLY,
                expireAt, "tx_001", "PLAY_STORE");

        //then
        // 상태 검증
        assertThat(testUser.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(testUser.getPlanStatus()).isEqualTo(PlanStatus.ACTIVE);
        // 상호작용 검증
        then(userPlanRepository).should().save(any(UserPlan.class));
    }

    @Test
    @DisplayName("updatePlanByWebhook - RENEW 성공")
    void updatePlanByWebhook_renew_success() {
        //given
        // 만료 7일 이내 → 갱신 가능 기간, 미래 연장분 없음 → RENEW 성공
        Instant currentExpireAt = now.plus(7, ChronoUnit.DAYS);
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, currentExpireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(23, ChronoUnit.DAYS),
                currentExpireAt, "tx_001", "PLAY_STORE");
        given(userPlanRepository.existsByUser_IdAndStartAtGreaterThanEqualAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)))
                .willReturn(false);
        given(userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                eq(1L), any(), any(), eq(List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED))))
                .willReturn(Optional.of(currentPlan));

        //when
        userPlanService.updatePlanByWebhook(testUser, PlanAction.RENEW, PlanDuration.MONTHLY,
                null, "tx_002", "PLAY_STORE");

        //then
        then(userPlanRepository).should().save(any(UserPlan.class));
    }

    @Test
    @DisplayName("updatePlanByWebhook - CANCEL 성공 (Webhook = Apple이 이미 환불 처리)")
    void updatePlanByWebhook_cancel_success() {
        //given
        // Webhook CANCEL = Apple이 환불을 승인한 후 RevenueCat이 발송하는 이벤트
        // 환불 API 호출 없이 DB 상태만 CANCELLED/REFUNDED로 업데이트
        Instant expireAt = now.plus(14, ChronoUnit.DAYS);
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(16, ChronoUnit.DAYS),
                expireAt, "tx_001", "APP_STORE");
        given(userPlanRepository.findAllByUser_IdAndExpireAtAfterAndStatus(
                eq(1L), any(), eq(PlanStatus.ACTIVE)))
                .willReturn(List.of(currentPlan));

        //when
        userPlanService.updatePlanByWebhook(testUser, PlanAction.CANCEL, null,
                null, "tx_001", "APP_STORE");

        //then
        // 상태 검증: CANCELLED 마킹
        assertThat(currentPlan.getStatus()).isEqualTo(PlanStatus.CANCELLED);
        // 상호작용 검증: 환불 API 호출 없음 (Apple이 이미 처리)
        then(revenueCatRefundClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("updatePlanByWebhook - EXPIRE 성공 - 유저 마지막 플랜 만료 시 FREE 전환")
    void updatePlanByWebhook_expire_lastPlan_freeConversion() {
        //given
        // planExpireAt이 이미 과거 → 마지막 플랜 만료 → 유저를 FREE로 전환
        Instant expireAt = now.minus(1, ChronoUnit.SECONDS);
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan expiredPlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(30, ChronoUnit.DAYS),
                expireAt, "tx_001", "PLAY_STORE");
        given(userPlanRepository.findTopByUser_IdAndTransactionIdAndStatusInOrderByExpireAtDesc(
                eq(1L), eq("tx_001"), any()))
                .willReturn(Optional.of(expiredPlan));

        //when
        userPlanService.updatePlanByWebhook(testUser, PlanAction.EXPIRE, null,
                expireAt, "tx_001", "PLAY_STORE");

        //then
        // 상태 검증
        assertThat(expiredPlan.getStatus()).isEqualTo(PlanStatus.EXPIRED);
        assertThat(testUser.getPlan()).isEqualTo(PlanType.FREE);
    }

    // ACTIVE 상태의 UserPlan 생성 헬퍼
    private UserPlan buildActivePlan(PlanDuration duration, Instant startAt, Instant expireAt) {
        return UserPlan.builder()
                .id(1L).user(testUser).plan(PlanType.PREMIUM).duration(duration)
                .status(PlanStatus.ACTIVE).startAt(startAt).expireAt(expireAt)
                .build();
    }

    //Webhook용 메서드 추가
    private UserPlan buildActivePlan(PlanDuration duration, Instant startAt,
                                     Instant expireAt, String transactionId, String store) {
        return UserPlan.builder()
                .user(testUser)
                .plan(PlanType.PREMIUM)
                .duration(duration)
                .status(PlanStatus.ACTIVE)
                .startAt(startAt)
                .expireAt(expireAt)
                .transactionId(transactionId)
                .store(store)
                .build();
    }

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getName()).thenReturn(email);
        SecurityContextHolder.setContext(securityContext);
    }
}