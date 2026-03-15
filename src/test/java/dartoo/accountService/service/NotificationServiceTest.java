package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserNotification;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.NotificationStatus;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.dto.core.NotificationCreateRequest;
import dartoo.accountService.dto.core.NotificationListResponse;
import dartoo.accountService.dto.core.NotificationResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserNotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    // 테스트에서 자주 사용하는 기본 알림들
    private UserNotification unreadNotification;
    private UserNotification readNotification;
    private UserNotification oldNotification;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-03-13T00:00:00Z");

        // 모든 테스트에서 공통으로 사용하는 사용자
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

        //유형별 테스트
        unreadNotification = UserNotification.builder()
                .id(1L)
                .user(testUser)
                .title("읽지 않은 알림")
                .content("읽지 않은 알림 내용입니다.")
                .status(NotificationStatus.UNREAD)
                .createdAt(now.minus(1, ChronoUnit.DAYS))
                .build();

        readNotification = UserNotification.builder()
                .id(2L)
                .user(testUser)
                .title("읽은 알림")
                .content("읽은 알림 내용입니다.")
                .status(NotificationStatus.READ)
                .readAt(now.minus(1, ChronoUnit.HOURS))
                .createdAt(now.minus(2, ChronoUnit.DAYS))
                .build();

        oldNotification = UserNotification.builder()
                .id(3L)
                .user(testUser)
                .title("오래된 알림")
                .content("오래된 알림 내용입니다.")
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
    @DisplayName("알림 추가 성공")
    void addNotification_success() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));

        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setTitle("새로운 알림");
        request.setContent("새로운 알림 내용입니다.");

        UserNotification savedNotification = UserNotification.builder()
                .id(1L)
                .user(testUser)
                .title("새로운 알림")
                .content("새로운 알림 내용입니다.")
                .status(NotificationStatus.UNREAD)
                .createdAt(now)
                .build();

        given(userNotificationRepository.save(any(UserNotification.class)))
                .willReturn(savedNotification);

        // when
        NotificationResponse response = notificationService.addNotification(request);

        // then: 응답 검증
        assertThat(response.getTitle()).isEqualTo("새로운 알림");
        assertThat(response.getContent()).isEqualTo("새로운 알림 내용입니다.");
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.UNREAD);

        // then: Repository 호출 검증
        then(userNotificationRepository).should().save(argThat(notification ->
                notification.getUser().equals(testUser) &&
                        notification.getTitle().equals("새로운 알림") &&
                        notification.getContent().equals("새로운 알림 내용입니다.") &&
                        notification.getStatus().equals(NotificationStatus.UNREAD)));
    }

    @Test
    @DisplayName("알림 추가 실패 - 사용자를 찾을 수 없음")
    void addNotification_userNotFound() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setTitle("새로운 알림");
        request.setContent("새로운 알림 내용입니다.");

        // when, then
        assertThatThrownBy(() -> notificationService.addNotification(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);

        then(userNotificationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("알림 읽음으로 표시 성공")
    void markAsRead_success() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(1L, 1L))
                .willReturn(Optional.of(unreadNotification));

        // when
        NotificationResponse response = notificationService.markAsRead(1L);

        // then: 응답 검증
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("읽지 않은 알림");
        assertThat(response.getContent()).isEqualTo("읽지 않은 알림 내용입니다.");
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(response.getReadAt()).isNotNull();

        // then: 엔티티 상태 변경 확인
        assertThat(unreadNotification.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(unreadNotification.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("알림 읽음으로 표시 성공 - 이미 읽은 알림 재처리 (멱등성)")
    void markAsRead_success_alreadyRead() {
        // given: setUp의 readNotification 사용
        Instant previousReadAt = readNotification.getReadAt();

        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(2L, 1L))
                .willReturn(Optional.of(readNotification));

        // when
        NotificationResponse response = notificationService.markAsRead(2L);

        // then: 이미 READ 상태이므로 readAt이 업데이트됨
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(response.getReadAt()).isEqualTo(previousReadAt); // 최초로 읽은 시간만 기록하니까.
    }

    @Test
    @DisplayName("알림 읽음으로 표시 실패 - 알림을 찾을 수 없음")
    void markAsRead_notificationNotFound() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(999L, 1L))
                .willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> notificationService.markAsRead(999L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 읽음으로 표시 실패 - 사용자를 찾을 수 없음")
    void markAsRead_userNotFound() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> notificationService.markAsRead(1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }
    
    @Test
    @DisplayName("알림 전체 리스트 조회 성공 - 여러 알림 존재")
    void readAll_success_multipleNotifications() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findAllByUser_IdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED)))
                .willReturn(List.of(unreadNotification, readNotification, oldNotification)); // 최신순

        // when
        NotificationListResponse response = notificationService.readAll();

        // then
        assertThat(response.getNotificationList()).hasSize(3);

        // 첫 번째 알림
        assertThat(response.getNotificationList().get(0).getTitle()).isEqualTo("읽지 않은 알림");
        assertThat(response.getNotificationList().get(0).getStatus()).isEqualTo(NotificationStatus.UNREAD);

        // 두 번째 알림
        assertThat(response.getNotificationList().get(1).getTitle()).isEqualTo("읽은 알림");
        assertThat(response.getNotificationList().get(1).getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(response.getNotificationList().get(1).getReadAt()).isNotNull();

        // 세 번째 알림
        assertThat(response.getNotificationList().get(2).getTitle()).isEqualTo("오래된 알림");
        assertThat(response.getNotificationList().get(2).getStatus()).isEqualTo(NotificationStatus.UNREAD);

        // then: Repository 호출 검증
        then(userNotificationRepository).should()
                .findAllByUser_IdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(
                        eq(1L), any(Instant.class), eq(NotificationStatus.DELETED));
    }

    @Test
    @DisplayName("알림 전체 리스트 조회 성공 - 빈 리스트")
    void readAll_success_empty() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findAllByUser_IdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED)))
                .willReturn(List.of());

        // when
        NotificationListResponse response = notificationService.readAll();

        // then
        assertThat(response.getNotificationList()).isEmpty();
    }

    @Test
    @DisplayName("알림 전체 리스트 조회 실패 - 사용자를 찾을 수 없음")
    void readAll_userNotFound() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> notificationService.readAll())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 삭제 성공 - 소프트 삭제 (UNREAD → DELETED)")
    void deleteOne_success_unread() {
        // given: setUp의 unreadNotification 사용
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(1L, 1L))
                .willReturn(Optional.of(unreadNotification));

        // when
        notificationService.deleteOne(1L);

        // then: 엔티티 상태 검증
        assertThat(unreadNotification.getStatus()).isEqualTo(NotificationStatus.DELETED);

        // then: Repository 호출 검증
        then(userNotificationRepository).should().findByIdAndUser_Id(1L, 1L);
    }

    @Test
    @DisplayName("알림 삭제 성공 - 소프트 삭제 (READ → DELETED)")
    void deleteOne_success_read() {
        // given: setUp의 readNotification 사용
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(2L, 1L))
                .willReturn(Optional.of(readNotification));

        // when
        notificationService.deleteOne(2L);

        // then
        assertThat(readNotification.getStatus()).isEqualTo(NotificationStatus.DELETED);
    }

    @Test
    @DisplayName("알림 삭제 실패 - 알림을 찾을 수 없음")
    void deleteOne_notificationNotFound() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.findByIdAndUser_Id(999L, 1L))
                .willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> notificationService.deleteOne(999L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 삭제 실패 - 사용자를 찾을 수 없음")
    void deleteOne_userNotFound() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> notificationService.deleteOne(1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 모두 삭제 성공 - 여러 알림 삭제")
    void deleteAll_success_multipleNotifications() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.softDeleteAllVisible(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED)))
                .willReturn(5); // 5개 삭제됨

        // when
        notificationService.deleteAll();

        // then: Repository 호출 검증
        then(userNotificationRepository).should().softDeleteAllVisible(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED));
    }

    @Test
    @DisplayName("알림 모두 삭제 성공 - 삭제할 알림이 없는 경우")
    void deleteAll_success_noNotifications() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userNotificationRepository.softDeleteAllVisible(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED)))
                .willReturn(0); // 삭제된 알림 없음

        // when
        notificationService.deleteAll();

        // then: 예외 없이 정상 처리 (멱등성)
        then(userNotificationRepository).should().softDeleteAllVisible(
                eq(1L), any(Instant.class), eq(NotificationStatus.DELETED));
    }

    @Test
    @DisplayName("알림 모두 삭제 실패 - 사용자를 찾을 수 없음")
    void deleteAll_userNotFound() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> notificationService.deleteAll())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);

        then(userNotificationRepository).should(never()).softDeleteAllVisible(any(), any(), any());
    }

    @Test
    @DisplayName("현재 사용자 조회 성공")
    void getCurrentUser_success() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));

        // when
        UserEntity user = notificationService.getCurrentUser();

        // then
        assertThat(user).isEqualTo(testUser);
        assertThat(user.getUserEmail()).isEqualTo("test@test.com");
        assertThat(user.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("현재 사용자 조회 실패 - USER_NOT_FOUND")
    void getCurrentUser_fail() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> notificationService.getCurrentUser())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    // SecurityContext Mock 헬퍼 메서드
    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        given(securityContext.getAuthentication()).willReturn(auth);
        given(auth.getName()).willReturn(email);

        SecurityContextHolder.setContext(securityContext);
    }
}