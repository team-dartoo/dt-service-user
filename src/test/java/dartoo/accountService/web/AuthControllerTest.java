package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.domain.Gender;
import dartoo.accountService.domain.Role;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.dto.LoginRequestDto;
import dartoo.accountService.dto.TokenResponseDto;
import dartoo.accountService.dto.UserRequestDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.AuthService;
import dartoo.accountService.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static dartoo.accountService.error.ErrorCode.USER_ALREADY_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

//@MockBean - deprecated
// => @WebMvcTest + @Import(MockConfig) 로 대체해야 함

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AuthControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class AuthControllerTest {

    @TestConfiguration
    static class MockConfig{
        @Bean
        UserService userService() { return mock(UserService.class); }
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean AuthenticationManager authenticationManager() { return mock(AuthenticationManager.class); }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserService userService;
    @Autowired
    AuthService authService;
    @Autowired
    AuthenticationManager authenticationManager;

    private UserEntity testUser;
    private UserRequestDto dto;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .userEmail("test@test.com")
                .password("encodedPassword")
                .nickname("테스터")
                .role(Role.USER)
                .gender(Gender.MALE)
                .birthday(LocalDate.of(2000, 11, 16))
                .build();

        dto = new UserRequestDto();
        dto.setUserEmail(testUser.getUserEmail());
        dto.setPassword("password");
        dto.setBirthday(testUser.getBirthday());
        dto.setGender(testUser.getGender());
        dto.setNickname(testUser.getNickname());
    }

    @AfterEach
    void resetMocks(){
        reset(userService, authService, authenticationManager);
    }

    @Test
    @DisplayName("POST /api/auth/exist - 존재하면 true")
    void userExists_true() throws Exception {
        //given
        UserRequestDto dto2 = new UserRequestDto();
        dto2.setUserEmail("exist@test.com");
        given(userService.existUser(eq(dto2))).willReturn(true);

        //when, then
        mockMvc.perform(post("/api/auth/exist")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(userService, times(1)).existUser(eq(dto2));
    }

    @Test
    @DisplayName("POST /api/auth/exist - 없으면 false")
    void userExists_false() throws Exception {
        //given
        UserRequestDto dto2 = new UserRequestDto();
        dto2.setUserEmail("doesnotexist@test.com");
        given(userService.existUser(eq(dto2))).willReturn(false);

        //when,then
        mockMvc.perform(post("/api/auth/exist")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        verify(userService, times(1)).existUser(eq(dto2));
    }

    @Test
    @DisplayName("POST - /api/auth/signup - 정상적인 가입 성공 후 201 + email 반환")
    void signupCreated() throws Exception{
        //given
        given(userService.addUser(eq(dto))).willReturn(1L);
        given(userService.getUserById(1L)).willReturn(testUser);
        //when,then
        mockMvc.perform(post("/api/auth/signup")
                .contentType(APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@test.com"));
    }

    @Test
    @DisplayName("POST - /api/auth/signup - 중복 가입 시도시 409 응답 코드 반환")
    void signupConflict() throws Exception{
        //given
        doThrow(new ApiException(USER_ALREADY_EXISTS)).when(userService).addUser(eq(dto));
        //when,then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.path").value("/api/auth/signup"));
    }

    @Test
    @DisplayName("POST /api/auth/login - AccessToken과 Cookie 내 RefreshToken 반환 확인")
    void loginOk() throws Exception {
        //given
        LoginRequestDto login = new LoginRequestDto();
        login.setEmail("test@test.com");
        login.setPassword("password");
        given(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(login.getEmail(), login.getPassword())))
                .willReturn(new UsernamePasswordAuthenticationToken(login.getEmail(), login.getPassword(), List.of()));

        TokenResponseDto tokenResponseDto = new TokenResponseDto();
        tokenResponseDto.setAccessToken("accessToken");
        tokenResponseDto.setRefreshToken("refreshToken");
        given(authService.loginIssue(eq("test@test.com"), eq("device-id"), eq("user-agent"), any())).willReturn(tokenResponseDto);

        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(3);
//            ResponseCookie cookie = ResponseCookie.from("refresh_token", "refreshToken")
//                    .httpOnly(true)
//                    .secure(true)
//                    .sameSite("None")
//                    .maxAge(1209600)
//                    .path("/")
//                    .build();
//            response.addHeader("Set-Cookie", cookie.toString());
            response.addHeader("Set-Cookie", "refresh_token=" + tokenResponseDto.getRefreshToken() + "; Path=/");
            return tokenResponseDto;
        }).when(authService).loginIssue(eq("test@test.com"), eq("device-id"), eq("user-agent"), any());

        //when, then
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON_VALUE)
                        .header("X-Device-Id", "device-id")
                        .header("User-Agent", "user-agent")
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessToken"))
                .andReturn();

        MockHttpServletResponse resp = result.getResponse();
        String setCookie = resp.getHeader("Set-Cookie");
        assertThat(setCookie).contains("refresh_token=refreshToken");
//        assertThat(setCookie).contains("HttpOnly");
//        assertThat(setCookie).contains("Secure");
//        assertThat(setCookie).contains("Path=/");
//        assertThat(setCookie).contains("Max-Age=1209600");
    }

    @Test
    @DisplayName("POST /api/auth/login - 잘못된 자격증명시 401 응답 코드 반환")
    void loginBadCredential() throws Exception {
        //given
        LoginRequestDto wrongLogin = new LoginRequestDto();
        wrongLogin.setEmail("wrong@test.com"); wrongLogin.setPassword("wrongPassword");
        given(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(wrongLogin.getEmail(), wrongLogin.getPassword())))
                .willThrow(new BadCredentialsException("wrong password"));
        //when,then
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON_VALUE)
                .header("X-Device-Id", "device-id")
                .header("User-Agent", "user-agent")
                .content(objectMapper.writeValueAsString(wrongLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));

        verify(authService,never()).loginIssue(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("POST /api/auth/login - 헤더에서 device_id와 user_agent 부분이 빠지면 'unknown'으로 서비스 호출")
    void loginMissingHeaders() throws Exception {
        //given
        LoginRequestDto login = new LoginRequestDto();
        login.setEmail("test@test.com");
        login.setPassword("password");

        given(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(login.getEmail(), login.getPassword())))
                .willReturn(new UsernamePasswordAuthenticationToken(login.getEmail(), login.getPassword(), List.of()));

        TokenResponseDto tokenResponseDto = new TokenResponseDto();
        tokenResponseDto.setAccessToken("accessToken");
        tokenResponseDto.setRefreshToken("refreshToken");
        given(authService.loginIssue(eq("test@test.com"), eq("unknown"), eq("unknown"), any())).willReturn(tokenResponseDto);
        //when, then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessToken"));

        verify(authService).loginIssue(eq("test@test.com"), eq("unknown"), eq("unknown"), any());
    }

    private static final int MAX_AGE_REFRESH = 604800;
    private static final int MAX_AGE_RESTART = 1209600;

    @ParameterizedTest
    @MethodSource("refreshSource")
    @DisplayName("POST /api/auth/refresh - 정상적으로 RefreshToken 발급")
    void refreshSuccess(String url, boolean isRestart) throws Exception {
        //given
        TokenResponseDto tokenResponseDto = new TokenResponseDto();
        tokenResponseDto.setAccessToken("accessToken");
        tokenResponseDto.setRefreshToken("refreshToken");
        given(authService.refresh(eq("oldRefreshToken"),eq(isRestart),any())).willReturn(tokenResponseDto);

        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(2);
            ResponseCookie cookie = ResponseCookie.from("refresh_token", "refreshToken")
//                    .httpOnly(true)
//                    .secure(true)
//                    .sameSite("None")
                    .maxAge(isRestart ? MAX_AGE_RESTART : MAX_AGE_REFRESH)
//                    .path("/")
                    .build();
            response.addHeader("Set-Cookie", cookie.toString());
            return tokenResponseDto;
        }).when(authService).refresh(eq("oldRefreshToken"),eq(isRestart),any());

        //when,then
        MvcResult result = mockMvc.perform(post(url)
                .cookie(new Cookie("refresh_token","oldRefreshToken")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.accessToken").value("accessToken"))
                .andReturn();

        MockHttpServletResponse resp = result.getResponse();
        String setCookie = resp.getHeader("Set-Cookie");
        assertThat(setCookie).isNotEmpty();
        assertThat(setCookie).contains("refresh_token=refreshToken");
//        assertThat(setCookie).contains("HttpOnly");
//        assertThat(setCookie).contains("Secure");
//        assertThat(setCookie).contains("Path=/");
        int expectedAge = isRestart ? MAX_AGE_RESTART : MAX_AGE_REFRESH;
        assertThat(setCookie).contains("Max-Age="+expectedAge);

        verify(authService).refresh(eq("oldRefreshToken"),eq(isRestart),any());
    }

    @ParameterizedTest
    @MethodSource("refreshSource")
    @DisplayName("POST /api/auth/refresh - 쿠키가 비었거나 없을 경우 401 응답 코드 반환")
    void refreshMissingCookie(String url) throws Exception{
        //given
        mockMvc.perform(post(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_REFRESH_TOKEN"));

        mockMvc.perform(post(url)
                .cookie(new Cookie("refresh_token","")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_REFRESH_TOKEN"));

        verify(authService,times(2)).attachRefreshCookie(any(),eq(""),any());
        verify(authService,never()).refresh(anyString(),anyBoolean(),any());
    }

    @Test
    @DisplayName("POST /api/auth/logout - RefreshToken Revoke + 쿠키 폐기 후 응답 코드 204 반환")
    void logoutSuccess() throws Exception{
        mockMvc.perform(post("/api/auth/logout")
                .cookie(new Cookie("refresh_token","refreshToken"))
                .header("X-Device-Id", "device-id"))
                .andExpect(status().isNoContent());
        verify(authService, times(1)).revokeRefreshToken("refreshToken", "device-id");
        verify(authService, times(1)).attachRefreshCookie(any(),eq(""),any());
    }

    @Test
    @DisplayName("POST /api/auth/logout 시 X-Device-Id 헤더가 없을 경우 unknown을 deviceId로 사용")
    void logoutMissingDeviceId() throws Exception{
        mockMvc.perform(post("/api/auth/logout")
                .cookie(new Cookie("refresh_token","refreshToken")))
                .andExpect(status().isNoContent());
        verify(authService, times(1)).revokeRefreshToken("refreshToken", "unknown");
        verify(authService, times(1)).attachRefreshCookie(any(),eq(""),any());

    }

    static Stream<Arguments> refreshSource(){
        return Stream.of(
                Arguments.of("/api/auth/refresh", false),
                Arguments.of("/api/auth/refresh-restart", true)
        );
    }
}
/*
doAnswer을 이용해 HttpServletResponse에 들어갈 Cookie의 속성을 일일이 생성하고 검사할 수 있으나,
이미 서비스 단에서 검사하고 있기 때문에 존재 여부만 검사하고 주석 처리.
 */