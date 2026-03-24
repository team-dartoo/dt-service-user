package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.domain.enums.NotificationStatus;
import dartoo.accountService.dto.core.NotificationCreateRequest;
import dartoo.accountService.dto.core.NotificationListResponse;
import dartoo.accountService.dto.core.NotificationResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.NotificationService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static dartoo.accountService.error.ErrorCode.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({NotificationControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class NotificationControllerTest {

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

    @BeforeEach
    void setUp() {
        reset(notificationService);
    }

    @Test
    @DisplayName("GET /api/users/notifications - 조회 성공시 응답 코드 200")
    void getNotificationListSuccess() throws Exception {
        //given
        NotificationResponse notification1 = NotificationResponse.builder()
                .id(1L)
                .title("알림 제목 1")
                .content("알림 내용 1")
                .status(NotificationStatus.UNREAD)
                .createdAt(Instant.parse("2026-03-01T10:00:00Z"))
                .build();

        NotificationResponse notification2 = NotificationResponse.builder()
                .id(2L)
                .title("알림 제목 2")
                .content("알림 내용 2")
                .status(NotificationStatus.READ)
                .createdAt(Instant.parse("2026-03-02T10:00:00Z"))
                .readAt(Instant.parse("2026-03-02T11:00:00Z"))
                .build();

        NotificationListResponse response = NotificationListResponse.builder()
                .notificationList(List.of(notification1, notification2))
                .build();

        given(notificationService.readAll()).willReturn(response);
        //when, then
        mockMvc.perform(get("/api/users/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationList[0].id").value(1L))
                .andExpect(jsonPath("$.notificationList[0].title").value("알림 제목 1"))
                .andExpect(jsonPath("$.notificationList[0].content").value("알림 내용 1"))
                .andExpect(jsonPath("$.notificationList[0].status").value("UNREAD"))
                .andExpect(jsonPath("$.notificationList[1].id").value(2L))
                .andExpect(jsonPath("$.notificationList[1].title").value("알림 제목 2"))
                .andExpect(jsonPath("$.notificationList[1].content").value("알림 내용 2"))
                .andExpect(jsonPath("$.notificationList[1].status").value("READ"));
    }

    @Test
    @DisplayName("GET /api/users/notifications - 사용자가 없을 경우 404 응답을 반환")
    void getNotificationListFail() throws Exception {
        //given
        given(notificationService.readAll()).willThrow(new ApiException(USER_NOT_FOUND));
        //when,then
        mockMvc.perform(get("/api/users/notifications"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications"));
    }

    @Test
    @DisplayName("POST /api/users/notifications - 알림 추가 성공시 응답 코드 200")
    void addNotificationSuccess() throws Exception {
        //given
        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setTitle("새 알림 제목");
        request.setContent("새 알림 내용");

        NotificationResponse response = NotificationResponse.builder()
                .id(1L)
                .title("새 알림 제목")
                .content("새 알림 내용")
                .status(NotificationStatus.UNREAD)
                .createdAt(Instant.parse("2026-03-06T10:00:00Z"))
                .build();

        given(notificationService.addNotification(any(NotificationCreateRequest.class))).willReturn(response);
        //when,then
        mockMvc.perform(post("/api/users/notifications")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("새 알림 제목"))
                .andExpect(jsonPath("$.content").value("새 알림 내용"))
                .andExpect(jsonPath("$.status").value("UNREAD"));
    }

    @Test
    @DisplayName("POST /api/users/notifications - @Validated 오류 시 응답 코드 400 반환")
    void addNotification_validationError() throws Exception {
        //given
        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setTitle("");
        request.setContent("새 알림 내용");

        //when, then
        mockMvc.perform(post("/api/users/notifications")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications"));

        then(notificationService).should(never()).addNotification(any());
    }

    @Test
    @DisplayName("POST /api/users/notifications - JSON 형식 오류 시 응답 코드 400 반환")
    void addNotification_invalidJsonFormat() throws Exception {
        //given
        // JSON 파싱 단계에서 실패하므로 서비스 mock 세팅 불필요

        //when, then
        mockMvc.perform(post("/api/users/notifications")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content("invalid json format"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications"));

        then(notificationService).should(never()).addNotification(any());
    }

    @Test
    @DisplayName("POST /api/users/notifications - 사용자가 없을 경우 404 응답을 반환")
    void addNotificationUserNotFound() throws Exception {
        //given
        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setTitle("새 알림 제목");
        request.setContent("새 알림 내용");

        given(notificationService.addNotification(any(NotificationCreateRequest.class)))
                .willThrow(new ApiException(USER_NOT_FOUND));
        //when,then
        mockMvc.perform(post("/api/users/notifications")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications"));
    }

    @Test
    @DisplayName("PATCH /api/users/notifications/{id} - 알림 읽음 처리 성공시 응답 코드 200")
    void markNotificationAsReadSuccess() throws Exception {
        //given
        Long notificationId = 1L;
        NotificationResponse response = NotificationResponse.builder()
                .id(notificationId)
                .title("알림 제목")
                .content("알림 내용")
                .status(NotificationStatus.READ)
                .createdAt(Instant.parse("2026-03-06T10:00:00Z"))
                .readAt(Instant.parse("2026-03-06T11:00:00Z"))
                .build();

        given(notificationService.markAsRead(notificationId)).willReturn(response);
        //when,then
        mockMvc.perform(patch("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("알림 제목"))
                .andExpect(jsonPath("$.content").value("알림 내용"))
                .andExpect(jsonPath("$.status").value("READ"))
                .andExpect(jsonPath("$.readAt").exists());
    }

    @Test
    @DisplayName("PATCH /api/users/notifications/{id} - 알림이 존재하지 않을 경우 404 응답을 반환")
    void markNotificationAsReadNotFound() throws Exception {
        //given
        Long notificationId = 999L;
        given(notificationService.markAsRead(notificationId)).willThrow(new ApiException(NOTIFICATION_NOT_FOUND));
        //when,then
        mockMvc.perform(patch("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications/" + notificationId));
    }

    @Test
    @DisplayName("PATCH /api/users/notifications/{id} - 사용자가 없을 경우 404 응답을 반환")
    void markNotificationAsReadUserNotFound() throws Exception {
        //given
        Long notificationId = 1L;
        given(notificationService.markAsRead(notificationId)).willThrow(new ApiException(USER_NOT_FOUND));
        //when,then
        mockMvc.perform(patch("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications/" + notificationId));
    }

    @Test
    @DisplayName("DELETE /api/users/notifications/{id} - 알림 삭제 성공시 응답 코드 204 반환")
    void deleteOneSuccess() throws Exception {
        //given
        Long notificationId = 1L;
        //when, then
        mockMvc.perform(delete("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isNoContent());

        verify(notificationService, times(1)).deleteOne(eq(notificationId));
    }

    @Test
    @DisplayName("DELETE /api/users/notifications/{id} - 알림이 존재하지 않을 경우 404 응답을 반환")
    void deleteOneNotFound() throws Exception {
        //given
        Long notificationId = 999L;
        doThrow(new ApiException(NOTIFICATION_NOT_FOUND)).when(notificationService).deleteOne(eq(notificationId));
        //when,then
        mockMvc.perform(delete("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications/" + notificationId));
    }

    @Test
    @DisplayName("DELETE /api/users/notifications/{id} - 사용자가 없을 경우 404 응답을 반환")
    void deleteOneUserNotFound() throws Exception {
        //given
        Long notificationId = 1L;
        doThrow(new ApiException(USER_NOT_FOUND)).when(notificationService).deleteOne(eq(notificationId));
        //when,then
        mockMvc.perform(delete("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications/" + notificationId));
    }

    @Test
    @DisplayName("DELETE /api/users/notifications - 모든 알림 삭제 성공시 응답 코드 204 반환")
    void deleteAllSuccess() throws Exception {
        //given
        //when, then
        mockMvc.perform(delete("/api/users/notifications"))
                .andExpect(status().isNoContent());

        verify(notificationService, times(1)).deleteAll();
    }

    @Test
    @DisplayName("DELETE /api/users/notifications - 사용자가 없을 경우 404 응답을 반환")
    void deleteAllUserNotFound() throws Exception {
        //given
        doThrow(new ApiException(USER_NOT_FOUND)).when(notificationService).deleteAll();
        //when,then
        mockMvc.perform(delete("/api/users/notifications"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications"));
    }
}
