package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.domain.enums.NotificationStatus;
import dartoo.accountService.domain.enums.NotificationType;
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
        NotificationResponse notification1 = NotificationResponse.builder()
                .id(1L)
                .receptNo("20260301000001")
                .type(NotificationType.DISCLOSURE_UPDATE)
                .corpName("삼성전자")
                .corpCode("00126380")
                .title("알림 제목 1")
                .status(NotificationStatus.UNREAD)
                .createdAt(Instant.parse("2026-03-01T10:00:00Z"))
                .build();

        NotificationResponse notification2 = NotificationResponse.builder()
                .id(2L)
                .receptNo("20260302000002")
                .type(NotificationType.AI_SUMMARY)
                .corpName("SK하이닉스")
                .title("알림 제목 2")
                .status(NotificationStatus.READ)
                .createdAt(Instant.parse("2026-03-02T10:00:00Z"))
                .readAt(Instant.parse("2026-03-02T11:00:00Z"))
                .summaryLines(List.of("요약1"))
                .build();

        NotificationListResponse response = NotificationListResponse.builder()
                .notificationList(List.of(notification1, notification2))
                .build();

        given(notificationService.readAll()).willReturn(response);

        mockMvc.perform(get("/api/users/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationList[0].id").value(1L))
                .andExpect(jsonPath("$.notificationList[0].title").value("알림 제목 1"))
                .andExpect(jsonPath("$.notificationList[0].corpName").value("삼성전자"))
                .andExpect(jsonPath("$.notificationList[0].type").value("DISCLOSURE_UPDATE"))
                .andExpect(jsonPath("$.notificationList[0]._id").value("20260301000001"))
                .andExpect(jsonPath("$.notificationList[0].status").value("UNREAD"))
                .andExpect(jsonPath("$.notificationList[1].id").value(2L))
                .andExpect(jsonPath("$.notificationList[1].title").value("알림 제목 2"))
                .andExpect(jsonPath("$.notificationList[1].corpName").value("SK하이닉스"))
                .andExpect(jsonPath("$.notificationList[1].type").value("AI_SUMMARY"))
                .andExpect(jsonPath("$.notificationList[1].summaryLines[0]").value("요약1"))
                .andExpect(jsonPath("$.notificationList[1].status").value("READ"));
    }

    @Test
    @DisplayName("GET /api/users/notifications - 사용자가 없을 경우 404 응답을 반환")
    void getNotificationListFail() throws Exception {
        given(notificationService.readAll()).willThrow(new ApiException(USER_NOT_FOUND));
        mockMvc.perform(get("/api/users/notifications"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications"));
    }

    @Test
    @DisplayName("PATCH /api/users/notifications/{id} - 알림 읽음 처리 성공시 응답 코드 200")
    void markNotificationAsReadSuccess() throws Exception {
        Long notificationId = 1L;
        NotificationResponse response = NotificationResponse.builder()
                .id(notificationId)
                .title("알림 제목")
                .corpName("삼성전자")
                .type(NotificationType.DISCLOSURE_UPDATE)
                .status(NotificationStatus.READ)
                .createdAt(Instant.parse("2026-03-06T10:00:00Z"))
                .readAt(Instant.parse("2026-03-06T11:00:00Z"))
                .build();

        given(notificationService.markAsRead(notificationId)).willReturn(response);
        mockMvc.perform(patch("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("알림 제목"))
                .andExpect(jsonPath("$.corpName").value("삼성전자"))
                .andExpect(jsonPath("$.type").value("DISCLOSURE_UPDATE"))
                .andExpect(jsonPath("$.status").value("READ"))
                .andExpect(jsonPath("$.readAt").exists());
    }

    @Test
    @DisplayName("PATCH /api/users/notifications/{id} - 알림이 존재하지 않을 경우 404 응답을 반환")
    void markNotificationAsReadNotFound() throws Exception {
        Long notificationId = 999L;
        given(notificationService.markAsRead(notificationId)).willThrow(new ApiException(NOTIFICATION_NOT_FOUND));
        mockMvc.perform(patch("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications/" + notificationId));
    }

    @Test
    @DisplayName("PATCH /api/users/notifications/{id} - 사용자가 없을 경우 404 응답을 반환")
    void markNotificationAsReadUserNotFound() throws Exception {
        Long notificationId = 1L;
        given(notificationService.markAsRead(notificationId)).willThrow(new ApiException(USER_NOT_FOUND));
        mockMvc.perform(patch("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications/" + notificationId));
    }

    @Test
    @DisplayName("DELETE /api/users/notifications/{id} - 알림 삭제 성공시 응답 코드 204 반환")
    void deleteOneSuccess() throws Exception {
        Long notificationId = 1L;
        mockMvc.perform(delete("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isNoContent());

        verify(notificationService, times(1)).deleteOne(eq(notificationId));
    }

    @Test
    @DisplayName("DELETE /api/users/notifications/{id} - 알림이 존재하지 않을 경우 404 응답을 반환")
    void deleteOneNotFound() throws Exception {
        Long notificationId = 999L;
        doThrow(new ApiException(NOTIFICATION_NOT_FOUND)).when(notificationService).deleteOne(eq(notificationId));
        mockMvc.perform(delete("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications/" + notificationId));
    }

    @Test
    @DisplayName("DELETE /api/users/notifications/{id} - 사용자가 없을 경우 404 응답을 반환")
    void deleteOneUserNotFound() throws Exception {
        Long notificationId = 1L;
        doThrow(new ApiException(USER_NOT_FOUND)).when(notificationService).deleteOne(eq(notificationId));
        mockMvc.perform(delete("/api/users/notifications/{id}", notificationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications/" + notificationId));
    }

    @Test
    @DisplayName("DELETE /api/users/notifications - 모든 알림 삭제 성공시 응답 코드 204 반환")
    void deleteAllSuccess() throws Exception {
        mockMvc.perform(delete("/api/users/notifications"))
                .andExpect(status().isNoContent());

        verify(notificationService, times(1)).deleteAll();
    }

    @Test
    @DisplayName("DELETE /api/users/notifications - 사용자가 없을 경우 404 응답을 반환")
    void deleteAllUserNotFound() throws Exception {
        doThrow(new ApiException(USER_NOT_FOUND)).when(notificationService).deleteAll();
        mockMvc.perform(delete("/api/users/notifications"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/notifications"));
    }
}
