package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserNotification;
import dartoo.accountService.domain.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    Optional<UserNotification> findByIdAndUserId(Long id, Long userId);

    List<UserNotification> findAllByUserIdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(Long userId, Instant createdAtAfter, NotificationStatus status);
}
