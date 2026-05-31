package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.domain.enums.TokenPurpose;
import dartoo.accountService.dto.account.EmailActivationRequestDto;
import dartoo.accountService.dto.account.PasswordResetConfirmDto;
import dartoo.accountService.dto.account.PasswordResetRequestDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.EmailVerificationService;
import dartoo.accountService.service.UserService;
import org.junit.jupiter.api.AfterEach;
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

import static dartoo.accountService.error.ErrorCode.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({EmailControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class EmailControllerTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        UserService userService() { return mock(UserService.class); }
        @Bean
        EmailVerificationService emailVerificationService() { return mock(EmailVerificationService.class); }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired EmailVerificationService emailVerificationService;

    @AfterEach
    void resetMocks() {
        reset(userService, emailVerificationService);
    }

    // ── POST /api/auth/email/activation ──────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/email/activation - 인증 이메일 재발송 성공 시 200 반환")
    void resendActivationEmail_success() throws Exception {
        // given
        EmailActivationRequestDto dto = new EmailActivationRequestDto();
        dto.setEmail("test@test.com");
        willDoNothing().given(emailVerificationService).issueActivationEmail("test@test.com");
        // when, then
        mockMvc.perform(post("/api/auth/email/activation")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        then(emailVerificationService).should().issueActivationEmail("test@test.com");
    }

    @Test
    @DisplayName("POST /api/auth/email/activation - 발송 횟수 초과 시 429 반환")
    void resendActivationEmail_rateLimitExceeded() throws Exception {
        // given
        EmailActivationRequestDto dto = new EmailActivationRequestDto();
        dto.setEmail("test@test.com");
        willThrow(new ApiException(EMAIL_SEND_LIMIT_EXCEEDED))
                .given(emailVerificationService).issueActivationEmail("test@test.com");
        // when, then
        mockMvc.perform(post("/api/auth/email/activation")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_SEND_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.status").value(HttpStatus.TOO_MANY_REQUESTS.value()));
    }

    @Test
    @DisplayName("POST /api/auth/email/activation - SMTP 오류 시 503 반환")
    void resendActivationEmail_smtpFailed() throws Exception {
        // given
        EmailActivationRequestDto dto = new EmailActivationRequestDto();
        dto.setEmail("test@test.com");
        willThrow(new ApiException(EMAIL_SEND_FAILED))
                .given(emailVerificationService).issueActivationEmail("test@test.com");
        // when, then
        mockMvc.perform(post("/api/auth/email/activation")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_SEND_FAILED"));
    }

    // ── GET /api/auth/email/activate ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/email/activate - 유효한 토큰 클릭 시 200 반환")
    void activateEmail_success() throws Exception {
        // given
        given(emailVerificationService.verifyToken("valid-token", TokenPurpose.ACTIVATION))
                .willReturn("test@test.com");
        willDoNothing().given(userService).markEmailActivated("test@test.com");
        // when, then
        mockMvc.perform(get("/api/auth/email/activate")
                        .param("token", "valid-token"))
                .andExpect(status().isOk());

        then(userService).should().markEmailActivated("test@test.com");
    }

    @Test
    @DisplayName("GET /api/auth/email/activate - 존재하지 않는 토큰 클릭 시 404 반환")
    void activateEmail_tokenNotFound() throws Exception {
        // given
        given(emailVerificationService.verifyToken("invalid-token", TokenPurpose.ACTIVATION))
                .willThrow(new ApiException(EMAIL_TOKEN_NOT_FOUND));
        // when, then
        mockMvc.perform(get("/api/auth/email/activate")
                        .param("token", "invalid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/auth/email/activate - 만료된 토큰 클릭 시 401 반환")
    void activateEmail_tokenExpired() throws Exception {
        // given
        given(emailVerificationService.verifyToken("expired-token", TokenPurpose.ACTIVATION))
                .willThrow(new ApiException(EMAIL_TOKEN_EXPIRED));
        // when, then
        mockMvc.perform(get("/api/auth/email/activate")
                        .param("token", "expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("GET /api/auth/email/activate - 이미 사용된 토큰 클릭 시 409 반환")
    void activateEmail_tokenAlreadyUsed() throws Exception {
        // given
        given(emailVerificationService.verifyToken("used-token", TokenPurpose.ACTIVATION))
                .willThrow(new ApiException(EMAIL_TOKEN_ALREADY_USED));
        // when, then
        mockMvc.perform(get("/api/auth/email/activate")
                        .param("token", "used-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_ALREADY_USED"));
    }

    // ── POST /api/auth/password-reset/request ────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/password-reset/request - 이메일 존재 여부와 무관하게 200 반환")
    void requestPasswordReset_alwaysOk() throws Exception {
        // given
        PasswordResetRequestDto dto = new PasswordResetRequestDto();
        dto.setEmail("test@test.com");
        willDoNothing().given(userService).requestPasswordReset("test@test.com");
        // when, then
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        then(userService).should().requestPasswordReset("test@test.com");
    }

    // ── POST /api/auth/password-reset/confirm ────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/password-reset/confirm - 유효한 토큰 + 새 비밀번호 제출 시 200 반환")
    void confirmPasswordReset_success() throws Exception {
        // given
        PasswordResetConfirmDto dto = new PasswordResetConfirmDto();
        dto.setToken("valid-token");
        dto.setPassword("newPassword123");
        willDoNothing().given(userService).confirmPasswordReset("valid-token", "newPassword123");
        // when, then
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        then(userService).should().confirmPasswordReset("valid-token", "newPassword123");
    }

    @Test
    @DisplayName("POST /api/auth/password-reset/confirm - 만료된 토큰 제출 시 401 반환")
    void confirmPasswordReset_tokenExpired() throws Exception {
        // given
        PasswordResetConfirmDto dto = new PasswordResetConfirmDto();
        dto.setToken("expired-token");
        dto.setPassword("newPassword123");
        willThrow(new ApiException(EMAIL_TOKEN_EXPIRED))
                .given(userService).confirmPasswordReset("expired-token", "newPassword123");
        // when, then
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EMAIL_TOKEN_EXPIRED"));
    }

}
