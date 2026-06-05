package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserNotification;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.NotificationStatus;
import dartoo.accountService.domain.enums.NotificationType;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.repository.UserEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserNotificationRepositoryTest {

    @Autowired
    UserNotificationRepository userNotificationRepository;
    @Autowired
    UserEntityRepository userEntityRepository;
    @Autowired
    EntityManager entityManager;

    private UserEntity testUser;
    private UserNotification notification1;
    private UserNotification notification2;
    private UserNotification notification3;

    @BeforeEach
    void setUp() {
        //given: 테스트용 사용자 데이터 준비
        Instant now = Instant.now();
        testUser = UserEntity.builder()
                .userEmail("test@test.com")
                .password("password123")
                .nickname("tester")
                .birthday(LocalDate.of(2000, 11, 16))
                .role(Role.USER)
                .gender(Gender.MALE)
                .createdAt(now.minus(10, ChronoUnit.DAYS))
                .build();

        entityManager.persist(testUser);

        //읽은 알림, 안읽은 알림, 소프트 삭제된 알림
        notification1 = UserNotification.builder()
                .user(testUser)
                .title("알림1")
                .eventType(NotificationType.DISCLOSURE_UPDATE)
                .status(NotificationStatus.UNREAD)
                .createdAt(now.minus(7, ChronoUnit.DAYS))
                .build();

        notification2 = UserNotification.builder()
                .user(testUser)
                .title("알림2")
                .eventType(NotificationType.AI_SUMMARY)
                .status(NotificationStatus.READ)
                .createdAt(now.minus(5, ChronoUnit.DAYS))
                .build();

        notification3 = UserNotification.builder()
                .user(testUser)
                .title("알림3")
                .eventType(NotificationType.DISCLOSURE_UPDATE)
                .status(NotificationStatus.DELETED)
                .createdAt(now.minus(9, ChronoUnit.DAYS))
                .build();

        entityManager.persist(notification1);
        entityManager.persist(notification2);
        entityManager.persist(notification3);
        entityManager.flush();
        entityManager.clear();
    }

    @DisplayName("알림 ID와 사용자 ID로 알림 조회하기")
    @Test
    void findByIdAndUser_Id() {
        //given
        Long userId = testUser.getId();
        //when
        Optional<UserNotification> result = userNotificationRepository.findByIdAndUser_Id(notification1.getId(), userId);
        //then
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("알림1");
    }

    @DisplayName("사용자 ID와 생성일, 상태로 알림 목록 조회하기 - DELETED는 조회되지 않음")
    @Test
    void findAllByUser_IdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc() {
        //given
        Instant deadline = Instant.now().minus(90, ChronoUnit.DAYS);
        Long userId = testUser.getId();
        //when - 90일 이내 알림 중 삭제 안된것 중 최신순으로 사용자 알림 조회
        List<UserNotification> result = userNotificationRepository
                .findAllByUser_IdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(
                        userId, deadline, NotificationStatus.DELETED
                );
        //then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserNotification::getStatus)
                .containsExactlyInAnyOrder(NotificationStatus.UNREAD, NotificationStatus.READ);
    }

    @DisplayName("생성일 이전의 알림 삭제하기")
    @Test
    void deleteByCreatedAtBefore() {
        //given
        Instant deadline = Instant.now().plus(1, ChronoUnit.DAYS);
        //when
        long deleted = userNotificationRepository.deleteByCreatedAtBefore(deadline);
        //then
        assertThat(deleted).isEqualTo(3);
    }

    @DisplayName("사용자의 보이는 알림 전체 소프트 삭제하기")
    @Test
    void softDeleteAllVisible() {
        //given
        Instant deadline = Instant.now().minus(90, ChronoUnit.DAYS);
        //when
        int updated = userNotificationRepository.softDeleteAllVisible(
                testUser.getId(), deadline, NotificationStatus.DELETED
        );
        //then
        assertThat(updated).isEqualTo(2); // UNREAD, READ 2개만 DELETED로 변경
    }
}
