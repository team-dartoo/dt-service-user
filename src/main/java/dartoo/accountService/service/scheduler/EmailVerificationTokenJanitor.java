package dartoo.accountService.service.scheduler;

import dartoo.accountService.repository.EmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationTokenJanitor {

    private final EmailVerificationTokenRepository tokenRepository;

    @Scheduled(cron = "0 */30 * * * *")
    public void purgeExpiredTokens() {
        // 만료·사용 완료 모두 24시간 유예 적용
        // → Rate Limit 윈도우(1시간) 내 이력이 삭제되지 않아 우회 불가
        Instant cutoff = Instant.now().minusSeconds(24 * 3600);
        long deleted = tokenRepository.deleteByExpiredAtBefore(cutoff);
        deleted += tokenRepository.deleteByIsUsedTrueAndCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("만료된 이메일 인증 토큰 총 {} 개가 삭제되었습니다", deleted);
        }
    }
}
