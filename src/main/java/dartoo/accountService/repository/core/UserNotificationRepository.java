package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
}
