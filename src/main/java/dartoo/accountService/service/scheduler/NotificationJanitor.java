package dartoo.accountService.service.scheduler;

import dartoo.accountService.domain.enums.NotificationStatus;
import dartoo.accountService.repository.core.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationJanitor {
    //DELETE 상태로 90일이 지난 알림은 제거
    private static final Duration NOTIFICATION_TTL = Duration.ofDays(90);
    private final UserNotificationRepository userNotificationRepository;

    @Scheduled(cron = "0 0 4 * * *")
    public void purgeDeletedNotifications(){
        Instant deadline = Instant.now().minus(NOTIFICATION_TTL);
        long deleted = userNotificationRepository.deleteCreatedAtBefore(deadline);
        if (deleted > 0) {
            log.info("생성 90일 초과 알림 {}개 hard delete", deleted);
        }
    }
}
