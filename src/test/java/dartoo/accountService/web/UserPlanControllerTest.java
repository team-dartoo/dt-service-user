package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.dto.core.*;
import dartoo.accountService.dto.core.enums.PlanAction;
import dartoo.accountService.dto.core.enums.PlanDuration;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.UserPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static dartoo.accountService.error.ErrorCode.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserPlanController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({UserPlanControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class UserPlanControllerTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserPlanService userPlanService;

    @TestConfiguration
    static class MockConfig {
        @Bean UserPlanService userPlanService() { return mock(UserPlanService.class); }
    }

    @BeforeEach
    void setUp() {
        reset(userPlanService);
    }

    // ========== GET /api/users/plan 테스트 ==========

    @Test
    @DisplayName("GET /api/users/plan - 현재 플랜 조회 성공 (FREE) 시 응답 코드 200")
    void getCurrentPlanSuccess_free() throws Exception {
        // given
        // planExpireAt이 null일 때 JSON 응답에 필드 자체가 없는지 검증
        PlanResponse response = PlanResponse.builder()
                .plan(PlanType.FREE)
                .planStatus(PlanStatus.ACTIVE)
                .planExpireAt(null)
                .build();

        given(userPlanService.getCurrentPlan()).willReturn(response);

        // when, then
        mockMvc.perform(get("/api/users/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.planStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.planExpireAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/users/plan - 현재 플랜 조회 성공 (PREMIUM) 시 응답 코드 200")
    void getCurrentPlanSuccess_premium() throws Exception {
        // given
        Instant expireAt = Instant.parse("2026-03-15T00:00:00Z");
        PlanResponse response = PlanResponse.builder()
                .plan(PlanType.PREMIUM)
                .planStatus(PlanStatus.ACTIVE)
                .planExpireAt(expireAt)
                .build();

        given(userPlanService.getCurrentPlan()).willReturn(response);

        // when, then
        mockMvc.perform(get("/api/users/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.planStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.planExpireAt").value("2026-03-15T00:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/users/plan - 사용자가 없을 경우 404 응답을 반환")
    void getCurrentPlanUserNotFound() throws Exception {
        // given
        given(userPlanService.getCurrentPlan()).willThrow(new ApiException(USER_NOT_FOUND));

        // when, then
        mockMvc.perform(get("/api/users/plan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/plan"));
    }

    // ========== GET /api/users/plan/histories 테스트 ==========

    @Test
    @DisplayName("GET /api/users/plan/histories - 플랜 히스토리 조회 성공 시 응답 코드 200")
    void getHistorySuccess() throws Exception {
        // given
        Instant now = Instant.parse("2026-03-15T00:00:00Z");
        PlanHistoryResponse history1 = PlanHistoryResponse.builder()
                .plan(PlanType.PREMIUM)
                .status(PlanStatus.ACTIVE)
                .startAt(now.minus(30, ChronoUnit.DAYS))
                .expireAt(now.plus(30, ChronoUnit.DAYS))
                .build();

        PlanHistoryResponse history2 = PlanHistoryResponse.builder()
                .plan(PlanType.PREMIUM)
                .status(PlanStatus.CANCELLED)
                .startAt(now.minus(60, ChronoUnit.DAYS))
                .expireAt(now.minus(30, ChronoUnit.DAYS))
                .build();

        List<PlanHistoryResponse> historyList = Arrays.asList(history1, history2);
        PlanHistoryListResponse response = PlanHistoryListResponse.builder()
                .planHistoryList(historyList)
                .build();

        given(userPlanService.getHistory()).willReturn(response);

        // when, then
        mockMvc.perform(get("/api/users/plan/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planHistoryList").isArray())
                .andExpect(jsonPath("$.planHistoryList.length()").value(2))
                .andExpect(jsonPath("$.planHistoryList[0].plan").value("PREMIUM"))
                .andExpect(jsonPath("$.planHistoryList[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.planHistoryList[1].status").value("CANCELLED"));
    }

    @Test
    @DisplayName("GET /api/users/plan/histories - 히스토리가 없을 경우 빈 배열 반환")
    void getHistorySuccess_empty() throws Exception {
        // given
        PlanHistoryListResponse response = PlanHistoryListResponse.builder()
                .planHistoryList(List.of())
                .build();

        given(userPlanService.getHistory()).willReturn(response);

        // when, then
        mockMvc.perform(get("/api/users/plan/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planHistoryList").isArray())
                .andExpect(jsonPath("$.planHistoryList.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/users/plan/histories - 사용자가 없을 경우 404 응답을 반환")
    void getHistoryUserNotFound() throws Exception {
        // given
        given(userPlanService.getHistory()).willThrow(new ApiException(USER_NOT_FOUND));

        // when, then
        mockMvc.perform(get("/api/users/plan/histories"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/plan/histories"));
    }

    // ========== PATCH /api/users/plan - SUBSCRIBE 테스트 ==========

    @Test
    @DisplayName("PATCH /api/users/plan - 신규 구독 성공 (월간) 시 응답 코드 200")
    void updatePlanSubscribeSuccess_monthly() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .build();

        Instant expireAt = Instant.now().plus(30, ChronoUnit.DAYS);
        PlanUpdateResponse response = PlanUpdateResponse.builder()
                .action(PlanAction.SUBSCRIBE)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .status(PlanStatus.ACTIVE)
                .expireAt(expireAt)
                .accessToken("new.access.token.here")
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class))).willReturn(response);

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("SUBSCRIBE"))
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.duration").value("MONTHLY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.expireAt").exists())
                .andExpect(jsonPath("$.accessToken").value("new.access.token.here"));
    }

    @Test
    @DisplayName("PATCH /api/users/plan - @Valid 검증 실패 (action 누락) 시 응답 코드 400")
    void updatePlanValidationError_missingAction() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(null) // @NotNull 위반
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .build();

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/plan"));

        then(userPlanService).should(never()).updatePlan(any());
    }

    @Test
    @DisplayName("PATCH /api/users/plan - JSON 형식 오류 시 응답 코드 400")
    void updatePlanInvalidJsonFormat() throws Exception {
        // given
        // JSON 파싱 단계에서 실패하므로 서비스 mock 세팅 불필요

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content("invalid json format"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/plan"));
    }

    @Test
    @DisplayName("PATCH /api/users/plan - 이미 무료 체험 사용한 경우 409 응답을 반환")
    void updatePlanSubscribeTrialAlreadyUsed() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.TRIAL)
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class)))
                .willThrow(new ApiException(TRIAL_ALREADY_USED));

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIAL_ALREADY_USED"))
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.path").value("/api/users/plan"));
    }

    @Test
    @DisplayName("PATCH /api/users/plan - 유료 고객의 무료 체험 시도 시 403 응답을 반환")
    void updatePlanSubscribeTrialForExistingCustomer() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.TRIAL)
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class)))
                .willThrow(new ApiException(TRIAL_NOT_ALLOWED_FOR_EXISTING_CUSTOMER));

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TRIAL_NOT_ALLOWED_FOR_EXISTING_CUSTOMER"))
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()))
                .andExpect(jsonPath("$.path").value("/api/users/plan"));
    }

    @Test
    @DisplayName("PATCH /api/users/plan - 이미 구독 중일 때 SUBSCRIBE 시도 시 409 응답을 반환")
    void updatePlanSubscribeAlreadySubscribed() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class)))
                .willThrow(new ApiException(ALREADY_SUBSCRIBED));

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBSCRIBED"))
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()));
    }

    // ========== PATCH /api/users/plan - RENEW 테스트 ==========

    @Test
    @DisplayName("PATCH /api/users/plan - 구독 연장 성공 시 응답 코드 200")
    void updatePlanRenewSuccess() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.RENEW)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .build();

        Instant expireAt = Instant.now().plus(60, ChronoUnit.DAYS);
        PlanUpdateResponse response = PlanUpdateResponse.builder()
                .action(PlanAction.RENEW)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .status(PlanStatus.ACTIVE)
                .expireAt(expireAt)
                .accessToken("new.access.token.here")
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class))).willReturn(response);

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("RENEW"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("PATCH /api/users/plan - FREE 사용자의 연장 시도 시 400 응답을 반환")
    void updatePlanRenewInvalidRequest() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.RENEW)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class)))
                .willThrow(new ApiException(INVALID_RENEW_REQUEST));

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RENEW_REQUEST"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    @DisplayName("PATCH /api/users/plan - 이미 미래 연장이 존재할 때 409 응답을 반환")
    void updatePlanRenewAlreadyRenewed() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.RENEW)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class)))
                .willThrow(new ApiException(ALREADY_RENEWED));

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RENEWED"))
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()));
    }

    @Test
    @DisplayName("PATCH /api/users/plan - 연장 가능 기간 이전 시도 시 400 응답을 반환")
    void updatePlanRenewNotAllowedYet() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.RENEW)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class)))
                .willThrow(new ApiException(RENEW_NOT_ALLOWED_YET));

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RENEW_NOT_ALLOWED_YET"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    // ========== PATCH /api/users/plan - CANCEL 테스트 ==========

    @Test
    @DisplayName("PATCH /api/users/plan - 구독 취소 성공 시 응답 코드 200")
    void updatePlanCancelSuccess() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.CANCEL)
                .build();

        Instant expireAt = Instant.now().plus(15, ChronoUnit.DAYS);
        PlanUpdateResponse response = PlanUpdateResponse.builder()
                .action(PlanAction.CANCEL)
                .plan(PlanType.PREMIUM)
                .duration(null)
                .status(PlanStatus.CANCELLED)
                .expireAt(expireAt)
                .accessToken("new.access.token.here")
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class))).willReturn(response);

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CANCEL"))
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.duration").doesNotExist())
                .andExpect(jsonPath("$.expireAt").exists())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("PATCH /api/users/plan - 취소할 플랜이 없을 때 404 응답을 반환")
    void updatePlanCancelNoPlanToCancel() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.CANCEL)
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class)))
                .willThrow(new ApiException(PLAN_TO_CANCEL_NOT_FOUND));

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_TO_CANCEL_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    // ========== 공통 에러 케이스 테스트 ==========

    @Test
    @DisplayName("PATCH /api/users/plan - 사용자가 없을 경우 404 응답을 반환")
    void updatePlanUserNotFound() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class)))
                .willThrow(new ApiException(USER_NOT_FOUND));

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/plan"));
    }

    @Test
    @DisplayName("PATCH /api/users/plan - 잘못된 플랜 업데이트 요청 시 400 응답을 반환")
    void updatePlanInvalidRequest() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE)
                .plan(PlanType.FREE)
                .duration(PlanDuration.MONTHLY)
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class)))
                .willThrow(new ApiException(INVALID_PLAN_UPDATE_REQUEST));

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_UPDATE_REQUEST"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    @DisplayName("PATCH /api/users/plan - Duration 누락 시 400 응답을 반환")
    void updatePlanInvalidDuration() throws Exception {
        // given
        PlanUpdateRequest request = PlanUpdateRequest.builder()
                .action(PlanAction.SUBSCRIBE)
                .plan(PlanType.PREMIUM)
                .duration(null)
                .build();

        given(userPlanService.updatePlan(any(PlanUpdateRequest.class)))
                .willThrow(new ApiException(INVALID_PLAN_DURATION));

        // when, then
        mockMvc.perform(patch("/api/users/plan")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAN_DURATION"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }
}