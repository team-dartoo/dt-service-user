package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserNotification;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.NotificationStatus;
import dartoo.accountService.domain.enums.NotificationType;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.dto.core.NotificationListResponse;
import dartoo.accountService.dto.core.NotificationResponse;
import dartoo.accountService.dto.internal.BulkNotificationResponse;
import dartoo.accountService.dto.internal.InternalNotificationCreateRequest;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserNotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.NOTIFICATION_NOT_FOUND;
import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    UserNotificationRepository userNotificationRepository;
    @Mock
    UserEntityRepository userEntityRepository;

    @InjectMocks
    NotificationService notificationService;

    private UserEntity testUser;
    private Instant now;

    private UserNotification unreadNotification;
    private UserNotification readNotification;
    private UserNotification oldNotification;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-03-13T00:00:00Z");

        testUser = UserEntity.builder()
                .id(1L)
                .userEmail("test@test.com")
                .password("encodedPassword")
                .nickname("테스터")
                .role(Role.USER)
                .gender(Gender.MALE)
                .birthday(LocalDate.of(2000, 11, 16))
                .createdAt(now.minus(30, ChronoUnit.DAYS))
                .build();

        unreadNotification = UserNotification.builder()
                .id(1L)
                .user(testUser)
                .title("읽지 않은 알림")
                .receptNo("20260101000001")
                .corpName("삼성전자")
                .corpCode("00126380")
                .eventType(NotificationType.DISCLOSURE_UPDATE)
                .status(NotificationStatus.UNREAD)
                .createdAt(now.minus(1, ChronoUnit.DAYS))
                .build();

        readNotification = UserNotification.builder()
                .id(2L)
                .user(testUser)
                .title("읽은 알림")
                .receptNo("20260101000002")
                .corpName("SK하이닉스")
                .eventType(NotificationType.AI_SUMMARY)
                .status(NotificationStatus.READ)
                .readAt(now.minus(1, ChronoUnit.HOURS))
                .createdAt(now.minus(2, ChronoUnit.DAYS))
                .build();

        oldNotification = UserNotification.builder()
                .id(3L)
                .user(testUser)
                .title("오래된 알림")
                .corpName("LG")
                .eventType(NotificationType.DISCLOSURE_UPDATE)
                .status(NotificationStatus.UNREAD)
                .createdAt(now.minus(5, ChronoUnit.DAYS))
                .build();

        mockSecurityContext("test@test.com");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("알림 읽음으로 표시 성공")
    void markAsRead_success() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(1L, 1L))
                .willReturn(Optional.of(unreadNotification));

        NotificationResponse response = notificationService.markAsRead(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("읽지 않은 알림");
        assertThat(response.getCorpName()).isEqualTo("삼성전자");
        assertThat(response.getType()).isEqualTo(NotificationType.DISCLOSURE_UPDATE);
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(response.getReadAt()).isNotNull();

        assertThat(unreadNotification.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(unreadNotification.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("알림 읽음으로 표시 성공 - 이미 읽은 알림 재처리 (멱등성)")
    void markAsRead_success_alreadyRead() {
        Instant previousReadAt = readNotification.getReadAt();

        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(2L, 1L))
                .willReturn(Optional.of(readNotification));

        NotificationResponse response = notificationService.markAsRead(2L);

        assertThat(response.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(response.getReadAt()).isEqualTo(previousReadAt);
    }

    @Test
    @DisplayName("알림 읽음으로 표시 실패 - 알림을 찾을 수 없음")
    void markAsRead_notificationNotFound() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(999L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 읽음으로 표시 실패 - 사용자를 찾을 수 없음")
    void markAsRead_userNotFound() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 전체 리스트 조회 성공 - 여러 알림 존재")
    void readAll_success_multipleNotifications() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findAllByUser_IdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED)))
                .willReturn(List.of(unreadNotification, readNotification, oldNotification));

        NotificationListResponse response = notificationService.readAll();

        assertThat(response.getNotificationList()).hasSize(3);
        assertThat(response.getNotificationList().get(0).getTitle()).isEqualTo("읽지 않은 알림");
        assertThat(response.getNotificationList().get(0).getCorpName()).isEqualTo("삼성전자");
        assertThat(response.getNotificationList().get(0).getType()).isEqualTo(NotificationType.DISCLOSURE_UPDATE);
        assertThat(response.getNotificationList().get(0).getStatus()).isEqualTo(NotificationStatus.UNREAD);

        assertThat(response.getNotificationList().get(1).getTitle()).isEqualTo("읽은 알림");
        assertThat(response.getNotificationList().get(1).getType()).isEqualTo(NotificationType.AI_SUMMARY);
        assertThat(response.getNotificationList().get(1).getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(response.getNotificationList().get(1).getReadAt()).isNotNull();

        assertThat(response.getNotificationList().get(2).getTitle()).isEqualTo("오래된 알림");
        assertThat(response.getNotificationList().get(2).getStatus()).isEqualTo(NotificationStatus.UNREAD);

        then(userNotificationRepository).should()
                .findAllByUser_IdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(
                        eq(1L), any(Instant.class), eq(NotificationStatus.DELETED));
    }

    @Test
    @DisplayName("알림 전체 리스트 조회 성공 - 빈 리스트")
    void readAll_success_empty() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findAllByUser_IdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED)))
                .willReturn(List.of());

        NotificationListResponse response = notificationService.readAll();

        assertThat(response.getNotificationList()).isEmpty();
    }

    @Test
    @DisplayName("알림 전체 리스트 조회 실패 - 사용자를 찾을 수 없음")
    void readAll_userNotFound() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.readAll())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 삭제 성공 - 소프트 삭제 (UNREAD → DELETED)")
    void deleteOne_success_unread() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(1L, 1L))
                .willReturn(Optional.of(unreadNotification));

        notificationService.deleteOne(1L);

        assertThat(unreadNotification.getStatus()).isEqualTo(NotificationStatus.DELETED);
        then(userNotificationRepository).should().findByIdAndUser_Id(1L, 1L);
    }

    @Test
    @DisplayName("알림 삭제 성공 - 소프트 삭제 (READ → DELETED)")
    void deleteOne_success_read() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(2L, 1L))
                .willReturn(Optional.of(readNotification));

        notificationService.deleteOne(2L);

        assertThat(readNotification.getStatus()).isEqualTo(NotificationStatus.DELETED);
    }

    @Test
    @DisplayName("알림 삭제 실패 - 알림을 찾을 수 없음")
    void deleteOne_notificationNotFound() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteOne(999L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 삭제 실패 - 사용자를 찾을 수 없음")
    void deleteOne_userNotFound() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteOne(1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 모두 삭제 성공 - 여러 알림 삭제")
    void deleteAll_success_multipleNotifications() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.softDeleteAllVisible(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED)))
                .willReturn(5);

        notificationService.deleteAll();

        then(userNotificationRepository).should().softDeleteAllVisible(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED));
    }

    @Test
    @DisplayName("알림 모두 삭제 성공 - 삭제할 알림이 없는 경우")
    void deleteAll_success_noNotifications() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.softDeleteAllVisible(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED)))
                .willReturn(0);

        notificationService.deleteAll();

        then(userNotificationRepository).should().softDeleteAllVisible(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED));
    }

    @Test
    @DisplayName("알림 모두 삭제 실패 - 사용자를 찾을 수 없음")
    void deleteAll_userNotFound() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteAll())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);

        then(userNotificationRepository).should(never()).softDeleteAllVisible(any(), any(), any());
    }

    @Test
    @DisplayName("현재 사용자 조회 성공")
    void getCurrentUser_success() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));

        UserEntity user = notificationService.getCurrentUser();

        assertThat(user).isEqualTo(testUser);
        assertThat(user.getUserEmail()).isEqualTo("test@test.com");
        assertThat(user.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("현재 사용자 조회 실패 - USER_NOT_FOUND")
    void getCurrentUser_fail() {
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getCurrentUser())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @Test
    @DisplayName("bulkCreateInternal 매핑 정상 - DISCLOSURE/AI_SUMMARY/unknown/invalid userId 혼합")
    void bulkCreateInternal_매핑_정상() {
        // given
        InternalNotificationCreateRequest r1 = new InternalNotificationCreateRequest();
        r1.setUserId("1");
        r1.setTitle("공시 생성");
        r1.setReceptNo("20260101000001");
        r1.setCorpName("삼성전자");
        r1.setCorpCode("00126380");
        r1.setEventType("disclosure.created");
        r1.setSummaryLines(List.of("요약1", "요약2"));

        InternalNotificationCreateRequest r2 = new InternalNotificationCreateRequest();
        r2.setUserId("1");
        r2.setTitle("요약 업데이트");
        r2.setEventType("summary.updated");

        InternalNotificationCreateRequest r3 = new InternalNotificationCreateRequest();
        r3.setUserId("1");
        r3.setTitle("알수없음");
        r3.setEventType("unknown.event");

        InternalNotificationCreateRequest r4 = new InternalNotificationCreateRequest();
        r4.setUserId("notANumber");
        r4.setTitle("무효 userId");
        r4.setEventType("disclosure.created");

        InternalNotificationCreateRequest r5 = new InternalNotificationCreateRequest();
        r5.setUserId("999");
        r5.setTitle("사용자 없음");
        r5.setEventType("disclosure.updated");

        given(userEntityRepository.findAllById(any())).willReturn(List.of(testUser));

        // when
        BulkNotificationResponse result = notificationService.bulkCreateInternal(List.of(r1, r2, r3, r4, r5));

        // then — 응답 카운터 검증
        assertThat(result.getTotal()).isEqualTo(5);
        assertThat(result.getCreated()).isEqualTo(2);
        assertThat(result.getSkipped()).isEqualTo(3);

        // then — 저장된 알림 검증
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserNotification>> captor = ArgumentCaptor.forClass(List.class);
        then(userNotificationRepository).should().saveAll(captor.capture());
        List<UserNotification> saved = captor.getValue();

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getEventType()).isEqualTo(NotificationType.DISCLOSURE_UPDATE);
        assertThat(saved.get(0).getReceptNo()).isEqualTo("20260101000001");
        assertThat(saved.get(0).getCorpName()).isEqualTo("삼성전자");
        assertThat(saved.get(0).getSummaryLines()).containsExactly("요약1", "요약2");
        assertThat(saved.get(1).getEventType()).isEqualTo(NotificationType.AI_SUMMARY);
    }

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        lenient().when(securityContext.getAuthentication()).thenReturn(auth);
        lenient().when(auth.getName()).thenReturn(email);

        SecurityContextHolder.setContext(securityContext);
    }
}
