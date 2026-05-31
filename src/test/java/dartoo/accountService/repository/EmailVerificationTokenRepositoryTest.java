package dartoo.accountService.repository;

import dartoo.accountService.config.JpaAuditingConfig;
import dartoo.accountService.domain.EmailVerificationToken;
import dartoo.accountService.domain.enums.TokenPurpose;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class EmailVerificationTokenRepositoryTest {

    @Autowired
    EmailVerificationTokenRepository tokenRepository;
    @Autowired
    EntityManager entityManager;

    private static final String TEST_EMAIL = "test@test.com";
    private static final String OTHER_EMAIL = "other@test.com";
    private Instant now;

    // 시나리오별 토큰
    private EmailVerificationToken activeActivationToken;
    private EmailVerificationToken usedActivationToken;
    private EmailVerificationToken expiredActivationToken;      // expiredAt 지남, 24시간 이내
    private EmailVerificationToken oldExpiredActivationToken;   // expiredAt 지남, 24시간 초과
    private EmailVerificationToken activeResetToken;

    @BeforeEach
    void setUp() {
        now = Instant.now();

        // 유효한 인증 토큰
        activeActivationToken = EmailVerificationToken.builder()
                .email(TEST_EMAIL)
                .token("active-activation-token")
                .purpose(TokenPurpose.ACTIVATION)
                .expiredAt(now.plusSeconds(1800))
                .build();
        entityManager.persist(activeActivationToken);

        // 사용 완료된 인증 토큰
        usedActivationToken = EmailVerificationToken.builder()
                .email(TEST_EMAIL)
                .token("used-activation-token")
                .purpose(TokenPurpose.ACTIVATION)
                .expiredAt(now.plusSeconds(1800))
                .build();
        usedActivationToken.markAsUsed();
        entityManager.persist(usedActivationToken);

        // 만료된 인증 토큰 (24시간 이내)
        expiredActivationToken = EmailVerificationToken.builder()
                .email(TEST_EMAIL)
                .token("expired-activation-token")
                .purpose(TokenPurpose.ACTIVATION)
                .expiredAt(now.minusSeconds(60))
                .build();
        entityManager.persist(expiredActivationToken);

        // 만료 후 24시간 이상 지난 인증 토큰
        oldExpiredActivationToken = EmailVerificationToken.builder()
                .email(TEST_EMAIL)
                .token("old-expired-activation-token")
                .purpose(TokenPurpose.ACTIVATION)
                .expiredAt(now.minusSeconds(25 * 3600))
                .build();
        entityManager.persist(oldExpiredActivationToken);

        // 유효한 비밀번호 재설정 토큰
        activeResetToken = EmailVerificationToken.builder()
                .email(TEST_EMAIL)
                .token("active-reset-token")
                .purpose(TokenPurpose.RESET_PASSWORD)
                .expiredAt(now.plusSeconds(900))
                .build();
        entityManager.persist(activeResetToken);

        entityManager.flush();
        entityManager.clear();
    }

    // ── countByEmailAndPurposeAndCreatedAtAfter ───────────────────────────────

    @Test
    @DisplayName("1시간 이내에 생성된 ACTIVATION 토큰 수 집계")
    void countByEmailAndPurposeAndCreatedAtAfter_withinWindow() {
        // given — 방금 persist된 토큰들은 createdAt ≈ now
        Instant since = now.minusSeconds(3600);
        // when
        long count = tokenRepository.countByEmailAndPurposeAndCreatedAtAfter(TEST_EMAIL, TokenPurpose.ACTIVATION, since);
        // then — TEST_EMAIL + ACTIVATION 토큰 3개 (active, used, expired, oldExpired)
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("다른 이메일은 집계에 포함되지 않음")
    void countByEmailAndPurposeAndCreatedAtAfter_differentEmail() {
        // given
        Instant since = now.minusSeconds(3600);
        // when
        long count = tokenRepository.countByEmailAndPurposeAndCreatedAtAfter(OTHER_EMAIL, TokenPurpose.ACTIVATION, since);
        // then
        assertThat(count).isZero();
    }

    // ── findByTokenAndPurpose ─────────────────────────────────────────────────

    @Test
    @DisplayName("토큰 값과 purpose로 토큰 조회 성공")
    void findByTokenAndPurpose_found() {
        // when
        Optional<EmailVerificationToken> result = tokenRepository.findByTokenAndPurpose(
                "active-activation-token", TokenPurpose.ACTIVATION);
        // then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(result.get().isUsed()).isFalse();
    }

    @Test
    @DisplayName("토큰 값이 맞아도 purpose가 다르면 조회 실패")
    void findByTokenAndPurpose_wrongPurpose() {
        // when
        Optional<EmailVerificationToken> result = tokenRepository.findByTokenAndPurpose(
                "active-activation-token", TokenPurpose.RESET_PASSWORD);
        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 토큰 조회 시 empty 반환")
    void findByTokenAndPurpose_notFound() {
        // when
        Optional<EmailVerificationToken> result = tokenRepository.findByTokenAndPurpose(
                "nonexistent-token", TokenPurpose.ACTIVATION);
        // then
        assertThat(result).isEmpty();
    }

    // ── deleteByExpiredAtBefore ───────────────────────────────────────────────

    @Test
    @DisplayName("24시간 이상 지난 만료 토큰만 삭제 — 최근 만료 토큰은 유지")
    void deleteByExpiredAtBefore_only24hPlusRemoved() {
        // given — cutoff = now - 24h
        Instant cutoff = now.minusSeconds(24 * 3600);
        // when
        long deleted = tokenRepository.deleteByExpiredAtBefore(cutoff);
        entityManager.flush();
        // then — oldExpiredActivationToken 1개만 삭제
        assertThat(deleted).isEqualTo(1);
        assertThat(tokenRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("cutoff를 now로 설정 시 만료된 토큰 2개 삭제")
    void deleteByExpiredAtBefore_deleteAllExpired() {
        // when
        long deleted = tokenRepository.deleteByExpiredAtBefore(now);
        entityManager.flush();
        // then — expiredActivationToken + oldExpiredActivationToken = 2개
        assertThat(deleted).isEqualTo(2);
        assertThat(tokenRepository.count()).isEqualTo(3);
    }

    // ── deleteByIsUsedTrueAndCreatedAtBefore ──────────────────────────────────

    @Test
    @DisplayName("사용 완료된 토큰이 cutoff 이전에 생성된 경우 삭제")
    void deleteByIsUsedTrueAndCreatedAtBefore_deletesUsed() {
        // given — cutoff = now + 1분 (방금 생성된 토큰도 포함)
        Instant cutoff = now.plusSeconds(60);
        // when
        long deleted = tokenRepository.deleteByIsUsedTrueAndCreatedAtBefore(cutoff);
        entityManager.flush();
        // then — usedActivationToken 1개 삭제
        assertThat(deleted).isEqualTo(1);
        assertThat(tokenRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("cutoff보다 이후에 생성된 토큰은 used여도 삭제하지 않음")
    void deleteByIsUsedTrueAndCreatedAtBefore_keepsRecentUsed() {
        // given — cutoff = now - 1분 (방금 생성된 토큰보다 과거)
        Instant cutoff = now.minusSeconds(60);
        // when
        long deleted = tokenRepository.deleteByIsUsedTrueAndCreatedAtBefore(cutoff);
        entityManager.flush();
        // then — 삭제 없음 (createdAt ≈ now > cutoff)
        assertThat(deleted).isZero();
        assertThat(tokenRepository.count()).isEqualTo(5);
    }
}
