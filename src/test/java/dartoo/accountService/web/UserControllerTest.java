package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.dto.account.ChangePasswordDto;
import dartoo.accountService.dto.account.UserRequestDto;
import dartoo.accountService.dto.account.UserResponseDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.UserService;
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

import java.time.LocalDate;

import static dartoo.accountService.error.ErrorCode.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({UserControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class UserControllerTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @TestConfiguration
    static class MockConfig {
        @Bean UserService userService() { return mock(UserService.class); }
    }

    @BeforeEach
    void setUp() {
        reset(userService);
    }

    @Test
    @DisplayName("GET /api/users/info - 조회 성공시 응답 코드 200")
    void infoSuccess() throws Exception {
        //given
        UserResponseDto dto = new UserResponseDto();
        dto.setUserEmail("test@test.com");
        dto.setNickname("testNickname");
        dto.setGender(Gender.MALE);
        dto.setBirthday(LocalDate.of(2000,11,16));

        given(userService.readUser()).willReturn(dto);
        //when, then
        mockMvc.perform(get("/api/users/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value("test@test.com"))
                .andExpect(jsonPath("$.nickname").value("testNickname"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.birthday").value("2000-11-16"));
    }

    @Test
    @DisplayName("GET /api/users/info - 사용자가 없을 경우 404 응답을 반환")
    void infoFail() throws Exception {
        //given
        given(userService.readUser()).willThrow(new ApiException(USER_NOT_FOUND));
        //when,then
        mockMvc.perform(get("/api/users/info"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/info"));
    }

    @Test
    @DisplayName("PATCH /api/users/update - 프로필 업데이트 시 응답 코드 200")
    void updateSuccess() throws Exception {
        //given
        UserRequestDto dto = new UserRequestDto();
        dto.setNickname("newName");
        dto.setBirthday(LocalDate.of(2002,11,1));
        dto.setGender(Gender.MALE);

        UserResponseDto updated = new UserResponseDto();
        updated.setNickname("newName");
        updated.setBirthday(LocalDate.of(2002,11,1));
        updated.setGender(Gender.MALE);

        given(userService.updateUser(any(UserRequestDto.class))).willReturn(updated);
        //when,then
        mockMvc.perform(patch("/api/users/update")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("newName"))
                .andExpect(jsonPath("$.birthday").value("2002-11-01"))
                .andExpect(jsonPath("$.gender").value("MALE"));
    }

    @Test
    @DisplayName("PATCH /api/users/updated - @Validated 오류 시 , json 형식 오류 시 응답 코드 400 반환")
    @WithMockUser(username = "test@test.com", roles = "USER")
    void updateValidationError() throws Exception {
        //given
        UserRequestDto dto = new UserRequestDto();
        dto.setNickname("newName");
        dto.setBirthday(LocalDate.of(2002,11,1));
//        dto.setGender(Gender.MALE); @NotNull인데 넣지 않은 경우

        //when,then
        mockMvc.perform(patch("/api/users/update")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/update"));

        mockMvc.perform(patch("/api/users/update")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString("wrong format json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/update"));

        verify(userService, never()).updateUser(any());
    }

    @Test
    @DisplayName("PATCH - /api/users/update - 타인에 대한 정보 수정 시도시 응답 코드 403 반환")
    void updateAccessDenied() throws Exception {
        //given
        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("other@test.com"); // 세션 이메일과 다르게 설정
        dto.setNickname("validNickname");
        dto.setBirthday(LocalDate.of(2000, 1, 1));
        dto.setGender(Gender.MALE);

        doThrow(new AccessDeniedException("본인의 정보만 수정할 수 있습니다."))
                .when(userService).updateUser(any(UserRequestDto.class));
        //when,then
        mockMvc.perform(patch("/api/users/update")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()))
                .andExpect(jsonPath("$.path").value("/api/users/update"));
    }

    @Test
    @DisplayName("PATCH /api/users/password - 비밀번호 변경 성공시 응답 코드 204 반환")
    @WithMockUser(username = "test@test.com", roles = "USER")
    void passwordSuccess() throws Exception {
        //given
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("currentPassword");
        dto.setNewPassword("newPassword");
        //when,then
        mockMvc.perform(patch("/api/users/password")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        verify(userService, times(1)).changePassword(dto);
    }

    @Test
    @DisplayName("PATCH /api/users/password - @Validated 오류 시 응답 코드 400 반환")
    void passwordMissingFields() throws Exception {
        //given
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("currentPassword");
        dto.setNewPassword("new");
        //when,then
        mockMvc.perform(patch("/api/users/password")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/password"));
    }

    @Test
    @DisplayName("PATCH /api/users/password - 비밀번호 불일치 시 응답 코드 400 반환")
    void passwordCurrentDoesNotMatch() throws Exception {
        //given
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("wrongCurrentPassword");
        dto.setNewPassword("NewPassword");

        doThrow(new ApiException(PASSWORD_MISMATCH)).when(userService).changePassword(dto);
        //when,then
        mockMvc.perform(patch("/api/users/password")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/password"));
    }

    @Test
    @DisplayName("PATCH /api/users/password - 기존 비밀번호와 새 비밀번호가 동일한 경우 응답 코드 400 반환")
    void passwordSamePassword() throws Exception {
        //given
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("CurrentPassword");
        dto.setNewPassword("CurrentPassword");

        doThrow(new ApiException(SAME_PASSWORD)).when(userService).changePassword(dto);
        //when,then
        mockMvc.perform(patch("/api/users/password")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_PASSWORD"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/password"));
    }

    @Test
    @DisplayName("PATCH /api/users/password - 존재하지 않는 유저인 경우 응답 코드 404 반환")
    void passwordUserNotFound() throws Exception {
        //given
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("currentPassword");
        dto.setNewPassword("newPassword");

        doThrow(new ApiException(USER_NOT_FOUND)).when(userService).changePassword(dto);
        //when,then
        mockMvc.perform(patch("/api/users/password")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/password"));
    }

    @Test
    @DisplayName("DELETE /api/users - 성공적인 회원탈퇴인 경우 응답 코드 204 반환")
    void deleteUserSuccess() throws Exception {
        //given
        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("test@test.com");
        //when, then
        mockMvc.perform(delete("/api/users")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());

        verify(userService, times(1)).deleteUser(eq(dto));
    }

    @Test
    @DisplayName("DELETE /api/users - @Validated 에러 시 응답 코드 400 반환")
    void deleteUserValidatedError() throws Exception {
        //given
        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("");
        //when,then
        mockMvc.perform(delete("/api/users")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }

    @Test
    @DisplayName("DELETE /api/users - 권한이 없는 경우 응답 코드 403 반환")
    void deleteUserAccessDenied() throws Exception {
        //given
        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("attacker@test.com");

        doThrow(new AccessDeniedException("회원을 탈퇴할 권한이 없습니다."))
                .when(userService).deleteUser(eq(dto));
        //when,then
        mockMvc.perform(delete("/api/users")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }

    @Test
    @DisplayName("DELETE /api/users - 사용자 정보가 DB에 없는 경우 응답 코드 404 반환")
    void deleteUserNotFoundError() throws Exception {
        //given
        UserRequestDto dto = new UserRequestDto();
        dto.setUserEmail("notfound@test.com");

        doThrow(new ApiException(USER_NOT_FOUND)).when(userService).deleteUser(eq(dto));
        //when,then
        mockMvc.perform(delete("/api/users")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }
}