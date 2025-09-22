package dartoo.accountService.repository;

import dartoo.accountService.domain.RefreshToken;
import dartoo.accountService.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {
    void deleteAllByUserEntity(UserEntity userEntity);

    void deleteAllByUserEntityAndExpiredAtBefore(UserEntity user, Instant now);

    List<RefreshToken> findAllByUserEntityAndRevokedAtIsNullAndExpiredAtAfter(UserEntity user, Instant now);

    Optional<RefreshToken> findByUserEntityAndToken(UserEntity userEntity, String s);

    List<RefreshToken> findAllByUserEntityAndDidAndRevokedAtIsNullAndExpiredAtAfter(UserEntity user, String did, Instant now);

    long deleteByExpiredAtBefore(Instant now);
}
