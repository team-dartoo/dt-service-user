package dartoo.accountService.repository;

import dartoo.accountService.domain.UserAgreed;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreedRepository extends JpaRepository<UserAgreed,Long> {
}
