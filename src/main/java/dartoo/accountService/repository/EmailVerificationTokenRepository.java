package dartoo.accountService.repository;

import dartoo.accountService.domain.EmailVerificationToken;
import dartoo.accountService.domain.enums.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken,Long> {
    long countByEmailAndPurposeAndCreatedAtAfter(String email, TokenPurpose purpose, Instant createdAt);

    Optional<EmailVerificationToken> findByTokenAndPurpose(String token, TokenPurpose purpose);

    long deleteByExpiredAtBefore(Instant cutoff);

    long deleteByIsUsedTrueAndCreatedAtBefore(Instant cutoff);
}
