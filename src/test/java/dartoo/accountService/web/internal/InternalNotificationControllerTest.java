package dartoo.accountService.web.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.config.ServiceApiKeyFilter;
import dartoo.accountService.dto.internal.BulkInternalNotificationRequest;
import dartoo.accountService.dto.internal.BulkNotificationResponse;
import dartoo.accountService.dto.internal.InternalNotificationCreateRequest;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.reset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalNotificationController.class)
@Import({InternalNotificationControllerTest.MockConfig.class,
        InternalNotificationControllerTest.InternalApiSecurityConfig.class,
        GlobalExceptionAdvice.class, ServiceApiKeyFilter.class})
@TestPropertySource(properties = "app.security.worker-api-key=test-worker-key")
class InternalNotificationControllerTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    NotificationService notificationService;

    @TestConfiguration
    static class MockConfig {
        @Bean NotificationService notificationService() { return mock(NotificationService.class); }
    }

    @TestConfiguration
    static class InternalApiSecurityConfig {
        @Bean
        @Order(1)
        SecurityFilterChain internalApiFilterChain(HttpSecurity http, ServiceApiKeyFilter serviceApiKeyFilter) throws Exception {
            http
                .securityMatcher("/internal/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
                .addFilterBefore(serviceApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }

    @BeforeEach
    void setUp() {
        reset(notificationService);
        given(notificationService.bulkCreateInternal(any()))
                .willReturn(BulkNotificationResponse.builder()
                        .total(1).created(1).skipped(0).build());
    }

    private BulkInternalNotificationRequest sampleRequest(String eventType) {
        InternalNotificationCreateRequest r = new InternalNotificationCreateRequest();
        r.setUserId("1");
        r.setTitle("t");
        r.setEventType(eventType);
        BulkInternalNotificationRequest req = new BulkInternalNotificationRequest();
        req.setNotifications(List.of(r));
        return req;
    }

    @Test
    @DisplayName("POST /internal/api/notifications/bulk - 정상 페이로드 + 유효 API 키 → 200")
    void bulkCreateSuccess() throws Exception {
        mockMvc.perform(post("/internal/api/notifications/bulk")
                        .header("X-Service-API-Key", "test-worker-key")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(sampleRequest("disclosure.created"))))
                .andExpect(status().isOk());

        verify(notificationService).bulkCreateInternal(any());
    }

    @Test
    @DisplayName("POST /internal/api/notifications/bulk - API 키 누락 → 401")
    void bulkCreateMissingKey() throws Exception {
        mockMvc.perform(post("/internal/api/notifications/bulk")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(sampleRequest("disclosure.created"))))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).bulkCreateInternal(any());
    }

    @Test
    @DisplayName("POST /internal/api/notifications/bulk - 잘못된 API 키 → 401")
    void bulkCreateWrongKey() throws Exception {
        mockMvc.perform(post("/internal/api/notifications/bulk")
                        .header("X-Service-API-Key", "wrong-key-value")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(sampleRequest("disclosure.created"))))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).bulkCreateInternal(any());
    }

    @Test
    @DisplayName("POST /internal/api/notifications/bulk - unknown eventType (서비스에서 skip) → 200")
    void bulkCreateUnknownEventType() throws Exception {
        mockMvc.perform(post("/internal/api/notifications/bulk")
                        .header("X-Service-API-Key", "test-worker-key")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(sampleRequest("unknown.event"))))
                .andExpect(status().isOk());
    }
}
