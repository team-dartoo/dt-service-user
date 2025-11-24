package dartoo.accountService.repository;

import dartoo.accountService.domain.SocialProvider;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserOAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserOAuthRepository extends JpaRepository<UserOAuth,Long> {
    void deleteAllByUserEntity(UserEntity userEntity);

    Optional<UserOAuth> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
