package dartoo.accountService.web.internal;

import dartoo.accountService.config.ServiceApiKeyFilter;
import dartoo.accountService.domain.UserCorpBookmark;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPreference;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.repository.core.UserCorpBookmarkRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalNotificationSubscriberController.class)
@Import({InternalNotificationSubscriberControllerTest.MockConfig.class,
        InternalNotificationSubscriberControllerTest.InternalApiSecurityConfig.class,
        GlobalExceptionAdvice.class, ServiceApiKeyFilter.class})
@TestPropertySource(properties = "app.security.worker-api-key=test-worker-key")
class InternalNotificationSubscriberControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserCorpBookmarkRepository userCorpBookmarkRepository;

    @TestConfiguration
    static class MockConfig {
        @Bean UserCorpBookmarkRepository userCorpBookmarkRepository() { return mock(UserCorpBookmarkRepository.class); }
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
        reset(userCorpBookmarkRepository);
    }

    @Test
    void getPushEnabledSubscribersSuccess() throws Exception {
        UserEntity user1 = mock(UserEntity.class);
        UserEntity user5 = mock(UserEntity.class);
        UserEntity user10 = mock(UserEntity.class);
        UserCorpBookmark b1 = mock(UserCorpBookmark.class);
        UserCorpBookmark b5 = mock(UserCorpBookmark.class);
        UserCorpBookmark b10 = mock(UserCorpBookmark.class);

        given(user1.getId()).willReturn(1L);
        given(user1.getPreference()).willReturn(UserPreference.builder().userId(1L).pushEnabled(true).build());
        given(user5.getId()).willReturn(5L);
        given(user5.getPreference()).willReturn(null);
        given(user10.getId()).willReturn(10L);
        given(user10.getPreference()).willReturn(UserPreference.builder().userId(10L).pushEnabled(true).build());

        given(b1.getUser()).willReturn(user1);
        given(b5.getUser()).willReturn(user5);
        given(b10.getUser()).willReturn(user10);

        given(userCorpBookmarkRepository.findAllByCorpCode("00126380"))
                .willReturn(List.of(b1, b5, b10));

        mockMvc.perform(get("/internal/api/notifications/subscribers/by-corp-code/00126380")
                        .header("X-Service-API-Key", "test-worker-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value(1))
                .andExpect(jsonPath("$[1]").value(10));
    }

    @Test
    @DisplayName("GET /internal/api/notifications/subscribers/by-corp-code/{corpCode} - 결과 없음 → 200 + 빈 배열")
    void getPushEnabledSubscribersEmpty() throws Exception {
        given(userCorpBookmarkRepository.findAllByCorpCode("00000000"))
                .willReturn(List.of());

        mockMvc.perform(get("/internal/api/notifications/subscribers/by-corp-code/00000000")
                        .header("X-Service-API-Key", "test-worker-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /internal/api/notifications/subscribers/by-corp-code/{corpCode} - API 키 누락 → 401")
    void getPushEnabledSubscribersMissingKey() throws Exception {
        mockMvc.perform(get("/internal/api/notifications/subscribers/by-corp-code/00126380"))
                .andExpect(status().isUnauthorized());

        verify(userCorpBookmarkRepository, never()).findAllByCorpCode(any());
    }

    @Test
    @DisplayName("GET /internal/api/notifications/subscribers/by-corp-code/{corpCode} - 잘못된 API 키 → 401")
    void getPushEnabledSubscribersWrongKey() throws Exception {
        mockMvc.perform(get("/internal/api/notifications/subscribers/by-corp-code/00126380")
                        .header("X-Service-API-Key", "bad-key"))
                .andExpect(status().isUnauthorized());

        verify(userCorpBookmarkRepository, never()).findAllByCorpCode(any());
    }
}
