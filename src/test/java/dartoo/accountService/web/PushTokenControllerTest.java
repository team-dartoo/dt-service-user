package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.dto.push.PushTokenRegisterRequest;
import dartoo.accountService.dto.push.PushTokenResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.PushTokenProxyService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static dartoo.accountService.error.ErrorCode.EXTERNAL_SERVICE_ERROR;
import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.reset;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PushTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({PushTokenControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class PushTokenControllerTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    PushTokenProxyService pushTokenProxyService;

    @TestConfiguration
    static class MockConfig {
        @Bean PushTokenProxyService pushTokenProxyService() { return mock(PushTokenProxyService.class); }
    }

    @BeforeEach
    void setUp() {
        reset(pushTokenProxyService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test@example.com", null));
    }

    @Test
    @DisplayName("POST /api/users/notifications/push/tokens - 정상 등록 → 200")
    void registerPushTokenSuccess() throws Exception {
        PushTokenRegisterRequest req = PushTokenRegisterRequest.builder()
                .deviceId("device-123")
                .fcmToken("fcm-token-abc")
                .build();

        PushTokenResponse resp = PushTokenResponse.builder()
                .userId(1L)
                .deviceId("device-123")
                .fcmToken("fcm-token-abc")
                .build();

        given(pushTokenProxyService.registerPushToken("test@example.com", "device-123", "fcm-token-abc"))
                .willReturn(resp);

        mockMvc.perform(post("/api/users/notifications/push/tokens")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.deviceId").value("device-123"))
                .andExpect(jsonPath("$.fcmToken").value("fcm-token-abc"));
    }

    @Test
    @DisplayName("POST /api/users/notifications/push/tokens - 사용자 없음 → 404")
    void registerPushTokenUserNotFound() throws Exception {
        PushTokenRegisterRequest req = PushTokenRegisterRequest.builder()
                .deviceId("device-123")
                .fcmToken("fcm-token-abc")
                .build();

        given(pushTokenProxyService.registerPushToken("test@example.com", "device-123", "fcm-token-abc"))
                .willThrow(new ApiException(USER_NOT_FOUND));

        mockMvc.perform(post("/api/users/notifications/push/tokens")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/users/notifications/push/tokens - 외부 서비스 오류 → 502")
    void registerPushTokenExternalError() throws Exception {
        PushTokenRegisterRequest req = PushTokenRegisterRequest.builder()
                .deviceId("device-123")
                .fcmToken("fcm-token-abc")
                .build();

        given(pushTokenProxyService.registerPushToken("test@example.com", "device-123", "fcm-token-abc"))
                .willThrow(new ApiException(EXTERNAL_SERVICE_ERROR, "알림 서비스 호출 실패: HTTP 500"));

        mockMvc.perform(post("/api/users/notifications/push/tokens")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_SERVICE_ERROR"));
    }

    @Test
    @DisplayName("POST /api/users/notifications/push/tokens - deviceId 누락 → 400")
    void registerPushTokenMissingDeviceId() throws Exception {
        String body = """
                {"fcmToken": "fcm-token-abc"}
                """;

        mockMvc.perform(post("/api/users/notifications/push/tokens")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/users/notifications/push/tokens - fcmToken 누락 → 400")
    void registerPushTokenMissingFcmToken() throws Exception {
        String body = """
                {"deviceId": "device-123"}
                """;

        mockMvc.perform(post("/api/users/notifications/push/tokens")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
