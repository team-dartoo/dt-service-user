package dartoo.accountService.service;

import dartoo.accountService.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenJanitor {
    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 */30 * * * *")
    public void purgeExpiredRefreshTokens() {
        long deleted = refreshTokenRepository.deleteByExpiredAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired refresh tokens", deleted);
        }
    }
}
