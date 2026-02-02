package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPlanRepository extends JpaRepository<UserPlan,Long> {
}
