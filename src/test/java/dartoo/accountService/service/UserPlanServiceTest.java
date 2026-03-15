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
    @DisplayName("RENEW 성공 - YEARLY 구독 만료 30일 이내 연장")
    void updatePlan_renew_yearly_success() {
        // 현재 구독이 20일 후 만료 → 30일 이내이므로 연장 가능
        Instant expireAt = now.plus(20, ChronoUnit.DAYS);
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
    @DisplayName("RENEW 실패 - 갱신 가능 기간 전 (MONTHLY 14일 이전)")
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

    // ========== updatePlan - CANCEL 테스트 ==========

    @Test
    @DisplayName("CANCEL 성공 - ACTIVE 플랜 취소")
    void updatePlan_cancel_success() {
        Instant expireAt = now.plus(20, ChronoUnit.DAYS);
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(10, ChronoUnit.DAYS), expireAt);
        given(userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                eq(1L), any(), any(), eq(List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED))
        )).willReturn(Optional.of(currentPlan));

        given(userPlanRepository.findAllByUser_IdAndStartAtGreaterThanEqualAndStatusOrderByStartAtAsc(
                eq(1L), any(), eq(PlanStatus.ACTIVE)
        )).willReturn(List.of());

        given(tokenService.createAccessToken(any(), any())).willReturn("newAccessToken");

        PlanUpdateRequest request = PlanUpdateRequest.builder().action(PlanAction.CANCEL).build();

        PlanUpdateResponse response = userPlanService.updatePlan(request);

        assertThat(response.getAction()).isEqualTo(PlanAction.CANCEL);
        assertThat(response.getStatus()).isEqualTo(PlanStatus.CANCELLED);
        assertThat(currentPlan.getStatus()).isEqualTo(PlanStatus.CANCELLED);
    }

    @Test
    @DisplayName("CANCEL 성공 - 미래 연장분도 함께 취소됨")
    void updatePlan_cancel_withFuturePlans() {
        Instant expireAt = now.plus(20, ChronoUnit.DAYS);
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        testUser.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, expireAt);

        UserPlan currentPlan = buildActivePlan(PlanDuration.MONTHLY, now.minus(10, ChronoUnit.DAYS), expireAt);

        // 미래 연장분 1개 존재
        UserPlan futurePlan = UserPlan.builder()
                .id(2L).user(testUser).plan(PlanType.PREMIUM).duration(PlanDuration.MONTHLY)
                .status(PlanStatus.ACTIVE)
                .startAt(expireAt)
                .expireAt(expireAt.plus(30, ChronoUnit.DAYS))
                .build();

        given(userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                eq(1L), any(), any(), eq(List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED))
        )).willReturn(Optional.of(currentPlan));

        given(userPlanRepository.findAllByUser_IdAndStartAtGreaterThanEqualAndStatusOrderByStartAtAsc(
                eq(1L), any(), eq(PlanStatus.ACTIVE)
        )).willReturn(List.of(futurePlan));

        given(tokenService.createAccessToken(any(), any())).willReturn("newAccessToken");

        PlanUpdateRequest request = PlanUpdateRequest.builder().action(PlanAction.CANCEL).build();

        userPlanService.updatePlan(request);

        // 미래 연장분도 CANCELLED로 변경되어야 함
        assertThat(futurePlan.getStatus()).isEqualTo(PlanStatus.CANCELLED);
    }

    @Test
    @DisplayName("CANCEL 실패 - 취소할 플랜이 없음")
    void updatePlan_cancel_noPlanToCancel() {
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        given(userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                eq(1L), any(), any(), eq(List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED))
        )).willReturn(Optional.empty());

        PlanUpdateRequest request = PlanUpdateRequest.builder().action(PlanAction.CANCEL).build();

        assertThatThrownBy(() -> userPlanService.updatePlan(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", PLAN_TO_CANCEL_NOT_FOUND);
    }

    // ACTIVE 상태의 UserPlan 생성 헬퍼
    private UserPlan buildActivePlan(PlanDuration duration, Instant startAt, Instant expireAt) {
        return UserPlan.builder()
                .id(1L).user(testUser).plan(PlanType.PREMIUM).duration(duration)
                .status(PlanStatus.ACTIVE).startAt(startAt).expireAt(expireAt)
                .build();
    }

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        given(securityContext.getAuthentication()).willReturn(auth);
        given(auth.getName()).willReturn(email);
        SecurityContextHolder.setContext(securityContext);
    }
}