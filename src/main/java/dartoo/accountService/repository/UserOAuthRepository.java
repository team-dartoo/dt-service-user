package dartoo.accountService.repository;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserOAuth;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOAuthRepository extends JpaRepository<UserOAuth,Long> {
    void deleteAllByUserEntity(UserEntity userEntity);
}
