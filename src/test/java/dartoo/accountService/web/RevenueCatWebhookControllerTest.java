package dartoo.accountService.web;

import dartoo.accountService.dto.webhook.RevenueCatWebhookPayload;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.SearchHistoryService;
import dartoo.accountService.service.revenuecat.RevenueCatWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static dartoo.accountService.error.ErrorCode.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RevenueCatWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RevenueCatWebhookControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class RevenueCatWebhookControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired
    private RevenueCatWebhookService revenueCatWebhookService;

    @TestConfiguration
    static class MockConfig {
        @Bean
        RevenueCatWebhookService revenueCatWebhookService() { return mock(RevenueCatWebhookService.class); }
    }

    private static final String WEBHOOK_URL = "/api/webhooks/revenuecat";
    //RevenueCat
    private static final String VALID_PAYLOAD = """
            {
              "api_version": "1.0",
              "event": {
                "id": "event-tx-001",
                "type": "INITIAL_PURCHASE",
                "app_user_id": "user@test.com",
                "product_id": "dartoo_premium_monthly",
                "expiration_at_ms": 1791726653000,
                "transaction_id": "tx_001",
                "store": "PLAY_STORE"
              }
            }
            """;

    @Test
    @DisplayName("유효한 요청 → 200 OK")
    void handleWebhook_success() throws Exception {
        //given
        willDoNothing().given(revenueCatWebhookService)
                .handleWebhook(anyString(), any(RevenueCatWebhookPayload.class));

        //when & then
        mockMvc.perform(post(WEBHOOK_URL)
                        .header("Authorization", "valid-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Secret 불일치 → GlobalExceptionAdvice에 의해 401 반환")
    void handleWebhook_invalidSecret_401() throws Exception {
        //given
        willThrow(new ApiException(INVALID_WEBHOOK_SECRET))
                .given(revenueCatWebhookService)
                .handleWebhook(anyString(), any(RevenueCatWebhookPayload.class));

        //when & then
        mockMvc.perform(post(WEBHOOK_URL)
                        .header("Authorization", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authorization 헤더 누락 → 400 Bad Request (@RequestHeader 필수값)")
    void handleWebhook_missingAuthHeader_400() throws Exception {
        //given
        // Authorization 헤더 없이 요청

        //when & then
        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("등록되지 않은 product_id → GlobalExceptionAdvice에 의해 400 반환")
    void handleWebhook_invalidProductId_400() throws Exception {
        //given
        willThrow(new ApiException(INVALID_PRODUCT_ID))
                .given(revenueCatWebhookService)
                .handleWebhook(anyString(), any(RevenueCatWebhookPayload.class));

        //when & then
        mockMvc.perform(post(WEBHOOK_URL)
                        .header("Authorization", "valid-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isBadRequest());
    }
}