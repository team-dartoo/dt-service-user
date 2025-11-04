package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.dto.AgreedSettingsDto;
import dartoo.accountService.dto.PreferenceSettingsDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.UserSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static dartoo.accountService.error.ErrorCode.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    @WithMockUser(username = "test@test.com")
    @DisplayName("GET /api/users/settings/me/agreed - 사용자 약관 동의 조회 성공시 응답 코드 200 반환")
    void getMyAgreedSuccess() throws Exception{
        //given
        AgreedSettingsDto dto = new AgreedSettingsDto();
        dto.setPrivacyAgreed(true);
        dto.setPrivacyVersion("1.0.0");
        dto.setTosAgreed(true);
        dto.setTosVersion("1.0.0");
        dto.setMarketingAgreed(true);

        given(userSettingsService.getUserAgreedSettings("test@test.com")).willReturn(dto);
        //when, then
        mockMvc.perform(get("/api/users/settings/me/agreed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacyAgreed").value(true))
                .andExpect(jsonPath("$.privacyVersion").value("1.0.0"))
                .andExpect(jsonPath("$.tosAgreed").value(true))
                .andExpect(jsonPath("$.tosVersion").value("1.0.0"))
                .andExpect(jsonPath("$.marketingAgreed").value(true));

        verify(userSettingsService).getUserAgreedSettings("test@test.com");
    }

    @Test
    @WithMockUser(username = "test@test.com")
    @DisplayName("GET /api/users/settings/me/agreed - 사용자 약관 동의 조회 실패시 응답 코드 404 반환")
    void getMyAgreedFail() throws Exception{
        //given
        doThrow(new ApiException(USER_AGREED_NOT_FOUND))
                .when(userSettingsService).getUserAgreedSettings("test@test.com");
        //when,then
        mockMvc.perform(get("/api/users/settings/me/agreed"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_AGREED_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/settings/me/agreed"));
    }

    @Test
    @WithMockUser(username = "notexist@test.com")
    @DisplayName("GET /api/users/settings/me/agreed - 사용자 조회 실패시 응답 코드 404 반환")
    void getMyAgreedUserNotFound() throws Exception{
        //given
        doThrow(new ApiException(USER_NOT_FOUND))
                .when(userSettingsService).getUserAgreedSettings("notexist@test.com");
        //when,then
        mockMvc.perform(get("/api/users/settings/me/agreed"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/settings/me/agreed"));
    }

    @Test
    @WithMockUser(username = "test@test.com")
    @DisplayName("GET /api/users/settings/me/preference - 사용자 설정 조회 성공시 응답 코드 200 반환")
    void getMyPreferenceSuccess() throws Exception{
        //given
        PreferenceSettingsDto dto = new PreferenceSettingsDto();
        dto.setPushEnabled(true);
        dto.setEmailEnabled(true);
        dto.setAlertDelay(15);

        given(userSettingsService.getUserPreferenceSettings("test@test.com")).willReturn(dto);
        //when, then
        mockMvc.perform(get("/api/users/settings/me/preference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushEnabled").value(true))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.alertDelay").value(15));

        verify(userSettingsService).getUserPreferenceSettings("test@test.com");
    }

    @Test
    @WithMockUser(username = "test@test.com")
    @DisplayName("GET /api/users/settings/me/preference - 사용자 설정 조회 실패시 응답 코드 404 반환")
    void getMyPreferenceFail() throws Exception{
        //given
        doThrow(new ApiException(USER_PREFERENCE_NOT_FOUND))
                .when(userSettingsService).getUserPreferenceSettings("test@test.com");
        //when,then
        mockMvc.perform(get("/api/users/settings/me/preference"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_PREFERENCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/settings/me/preference"));
    }

    @Test
    @WithMockUser(username = "notexist@test.com")
    @DisplayName("GET /api/users/settings/me/preference - 사용자 조회 실패시 응답 코드 404 반환")
    void getMyPreferenceUserNotFound() throws Exception{
        //given
        doThrow(new ApiException(USER_NOT_FOUND))
                .when(userSettingsService).getUserPreferenceSettings("notexist@test.com");
        //when,then
        mockMvc.perform(get("/api/users/settings/me/preference"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/settings/me/preference"));
    }

    @Test
    @WithMockUser(username = "test@test.com")
    @DisplayName("PUT /api/users/settings/me/agreed - 사용자 약관 동의 수정 성공시 응답 코드 200 반환")
    void setMyAgreedSuccess() throws Exception{
        //given
        AgreedSettingsDto dto = new AgreedSettingsDto();
        dto.setPrivacyAgreed(true);
        dto.setPrivacyVersion("1.1.0");
        dto.setTosAgreed(true);
        dto.setTosVersion("1.1.0");
        dto.setMarketingAgreed(false);

        given(userSettingsService.updateUserAgreedSettings("test@test.com",dto)).willReturn(dto);
        //when,then
        mockMvc.perform(put("/api/users/settings/me/agreed")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacyAgreed").value(true))
                .andExpect(jsonPath("$.privacyVersion").value("1.1.0"))
                .andExpect(jsonPath("$.tosAgreed").value(true))
                .andExpect(jsonPath("$.tosVersion").value("1.1.0"))
                .andExpect(jsonPath("$.marketingAgreed").value(false));
    }

    @Test
    @WithMockUser(username = "test@test.com")
    @DisplayName("PUT /api/users/settings/me/agreed - @Validated 오류 시 응답 코드 400 반환")
    void setMyAgreedValidatedError() throws Exception{
        //given
        AgreedSettingsDto dto = new AgreedSettingsDto();
        //when,then
        mockMvc.perform(put("/api/users/settings/me/agreed")
                .contentType(APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/settings/me/agreed"));

    }

    @Test
    @WithMockUser(username = "notexist@test.com")
    @DisplayName("PUT /api/users/settings/me/agreed - 사용자 조회 실패시 응답 코드 404 반환")
    void setMyAgreedUserNotFound() throws Exception{
        //given
        AgreedSettingsDto dto = new AgreedSettingsDto();
        dto.setPrivacyAgreed(true);
        dto.setPrivacyVersion("1.1.0");
        dto.setTosAgreed(true);
        dto.setTosVersion("1.1.0");
        dto.setMarketingAgreed(false);

        doThrow(new ApiException(USER_NOT_FOUND))
                .when(userSettingsService).updateUserAgreedSettings("notexist@test.com",dto);
        //when,then
        mockMvc.perform(put("/api/users/settings/me/agreed")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/settings/me/agreed"));
    }

    @Test
    @WithMockUser(username = "test@test.com")
    @DisplayName("PUT /api/users/settings/me/preference - 사용자 설정 수정 성공시 응답 코드 200 반환")
    void setMyPreferenceSuccess() throws Exception{
        //given
        PreferenceSettingsDto dto = new PreferenceSettingsDto();
        dto.setPushEnabled(true);
        dto.setEmailEnabled(false);
        dto.setAlertDelay(1);

        given(userSettingsService.updateUserPreferenceSettings("test@test.com",dto)).willReturn(dto);
        //when,then
        mockMvc.perform(put("/api/users/settings/me/preference")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushEnabled").value(true))
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.alertDelay").value(1));
    }

    @Test
    @WithMockUser(username = "test@test.com")
    @DisplayName("PUT /api/users/settings/me/preference - @Validated 오류 시 응답 코드 400 반환")
    void setMyPreferenceValidatedError() throws Exception{
        //given
        PreferenceSettingsDto dto = new PreferenceSettingsDto();
        //when,then
        mockMvc.perform(put("/api/users/settings/me/preference")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/settings/me/preference"));

    }

    @Test
    @WithMockUser(username = "notexist@test.com")
    @DisplayName("PUT /api/users/settings/me/preference - 사용자 조회 실패시 응답 코드 404 반환")
    void setMyPreferenceUserNotFound() throws Exception{
        //given
        PreferenceSettingsDto dto = new PreferenceSettingsDto();
        dto.setPushEnabled(true);
        dto.setEmailEnabled(false);
        dto.setAlertDelay(1);

        doThrow(new ApiException(USER_NOT_FOUND))
                .when(userSettingsService).updateUserPreferenceSettings("notexist@test.com",dto);
        //when,then
        mockMvc.perform(put("/api/users/settings/me/preference")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/settings/me/preference"));
    }

}
/*
@WithMockUser(username = "", password = "", roles = "USER")
 -> MockMvc로 컨트롤러를 테스트할 때, 실제로 로그인하지 않아도
 인증된 사용자(SecurityContext의 Authentication 객체) 를 자동으로 만들어주는 역할.

UserSettingsController.java에

private String currentEmail(){
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth.getName();
}

코드가 있는데 MockMvc를 통해 테스트 할 경우 이 SecurityContextHolder가 비기 때문에 이를 채워주는 역할을 한다.
 */