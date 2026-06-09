package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.dto.oauth.OnBoardingRequestDto;
import dartoo.accountService.dto.oauth.OnBoardingResponseDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.OnBoardingService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import dartoo.accountService.domain.enums.Gender;

import java.time.LocalDate;

import static dartoo.accountService.error.ErrorCode.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnBoardingController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "test@test.com") // SecurityContextHolder 채우기 위해 필요
@Import({OnBoardingControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class OnBoardingControllerTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockMvc mockMvc;

    @Autowired
    OnBoardingService onBoardingService;

    @TestConfiguration
    static class MockConfig {
        @Bean OnBoardingService onBoardingService() { return mock(OnBoardingService.class); }
    }

    @BeforeEach
    void setUp() {
        reset(onBoardingService);
    }

    @Test
    @DisplayName("POST /api/users/onboarding/complete - 온보딩 성공시 응답 코드 200")
    void completeOnBoardingSuccess() throws Exception {
        //given
        OnBoardingRequestDto requestDto = OnBoardingRequestDto.builder()
                .email("test@test.com")
                .password("password123")
                .nickname("testNickname")
                .build();

        OnBoardingResponseDto responseDto = OnBoardingResponseDto.builder()
                .userEmail("test@test.com")
                .isPasswordSet(true)
                .nickname("testNickname")
                .build();

        given(onBoardingService.initOnBoarding(eq("test@test.com"), any(OnBoardingRequestDto.class)))
                .willReturn(responseDto);
        //when, then
        mockMvc.perform(post("/api/users/onboarding/complete")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value("test@test.com"))
                .andExpect(jsonPath("$.isPasswordSet").value(true))
                .andExpect(jsonPath("$.nickname").value("testNickname"));
    }

    @Test
    @DisplayName("POST /api/users/onboarding/complete - birthday와 gender를 포함해도 온보딩 성공 시 200 응답")
    void completeOnBoardingSuccessWithOptionalFields() throws Exception {
        //given
        OnBoardingRequestDto requestDto = OnBoardingRequestDto.builder()
                .email("test@test.com")
                .password("password123")
                .nickname("testNickname")
                .birthday(LocalDate.of(2000, 11, 16))
                .gender(Gender.MALE)
                .build();

        OnBoardingResponseDto responseDto = OnBoardingResponseDto.builder()
                .userEmail("test@test.com")
                .isPasswordSet(true)
                .nickname("testNickname")
                .birthday(LocalDate.of(2000, 11, 16))
                .gender(Gender.MALE)
                .build();

        given(onBoardingService.initOnBoarding(eq("test@test.com"), any(OnBoardingRequestDto.class)))
                .willReturn(responseDto);
        //when, then
        mockMvc.perform(post("/api/users/onboarding/complete")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthday").value("2000-11-16"))
                .andExpect(jsonPath("$.gender").value("MALE"));
    }

    @Test
    @DisplayName("POST /api/users/onboarding/complete - @Validated 오류 시 응답 코드 400 반환")
    void completeOnBoardingValidationError() throws Exception {
        //given
        OnBoardingRequestDto dto = OnBoardingRequestDto.builder()
                .email("test@test.com")
                .password("short") // @Size(min = 8) 위반
                .nickname("testNickname")
                .build();

        //when,then
        mockMvc.perform(post("/api/users/onboarding/complete")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/onboarding/complete"));

        verify(onBoardingService, never()).initOnBoarding(anyString(), any());
    }

    @Test
    @DisplayName("POST /api/users/onboarding/complete - nickname이 공백인 경우 응답 코드 400 반환")
    void completeOnBoardingNicknameBlank() throws Exception {
        //given
        OnBoardingRequestDto dto = OnBoardingRequestDto.builder()
                .email("test@test.com")
                .password("password123")
                .nickname("") // @NotBlank 위반
                .build();

        //when,then
        mockMvc.perform(post("/api/users/onboarding/complete")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/onboarding/complete"));

        verify(onBoardingService, never()).initOnBoarding(anyString(), any());
    }

    @Test
    @DisplayName("POST /api/users/onboarding/complete - json 형식 오류 시 응답 코드 400 반환")
    void completeOnBoardingInvalidJsonFormat() throws Exception {
        //given
        // JSON 파싱 단계에서 실패하므로 서비스 mock 세팅 불필요

        //when,then
        mockMvc.perform(post("/api/users/onboarding/complete")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString("wrong format json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/onboarding/complete"));

        verify(onBoardingService, never()).initOnBoarding(anyString(), any());
    }

    @Test
    @DisplayName("POST /api/users/onboarding/complete - 사용자가 없을 경우 404 응답을 반환")
    void completeOnBoardingUserNotFound() throws Exception {
        //given
        OnBoardingRequestDto dto = OnBoardingRequestDto.builder()
                .email("notfound@test.com")
                .password("password123")
                .nickname("testNickname")
                .build();

        given(onBoardingService.initOnBoarding(anyString(), any(OnBoardingRequestDto.class)))
                .willThrow(new ApiException(USER_NOT_FOUND));
        //when,then
        mockMvc.perform(post("/api/users/onboarding/complete")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/onboarding/complete"));
    }

    @Test
    @DisplayName("POST /api/users/onboarding/complete - 이미 온보딩이 완료된 경우 409 응답을 반환")
    void completeOnBoardingUserAlreadyOnboarded() throws Exception {
        //given
        OnBoardingRequestDto dto = OnBoardingRequestDto.builder()
                .email("test@test.com")
                .password("password123")
                .nickname("testNickname")
                .build();

        given(onBoardingService.initOnBoarding(anyString(), any(OnBoardingRequestDto.class)))
                .willThrow(new ApiException(USER_ALREADY_ONBOARDED));
        //when,then
        mockMvc.perform(post("/api/users/onboarding/complete")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_ONBOARDED"))
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.path").value("/api/users/onboarding/complete"));
    }

    @Test
    @DisplayName("POST /api/users/onboarding/complete - 타인의 정보로 온보딩 시도시 응답 코드 403 반환")
    void completeOnBoardingAccessDenied() throws Exception {
        //given
        OnBoardingRequestDto dto = OnBoardingRequestDto.builder()
                .email("other@test.com") // 인증된 이메일과 다른 경우
                .password("password123")
                .nickname("testNickname")
                .build();

        given(onBoardingService.initOnBoarding(anyString(), any(OnBoardingRequestDto.class)))
                .willThrow(new AccessDeniedException("사용자 정보가 일치하지 않습니다."));
        //when,then
        mockMvc.perform(post("/api/users/onboarding/complete")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()))
                .andExpect(jsonPath("$.path").value("/api/users/onboarding/complete"));
    }
}
