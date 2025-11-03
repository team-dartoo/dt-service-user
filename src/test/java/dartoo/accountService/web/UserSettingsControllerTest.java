package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.UserService;
import dartoo.accountService.service.UserSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@WebMvcTest(UserSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({UserSettingsControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class UserSettingsControllerTest {
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserSettingsService userSettingsService;

    @TestConfiguration
    static class MockConfig {
        @Bean
        UserSettingsService userSettingsService() { return mock(UserSettingsService.class); }
    }
}