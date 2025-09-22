package dartoo.accountService.repository;

import dartoo.accountService.domain.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference,Long> {
}
