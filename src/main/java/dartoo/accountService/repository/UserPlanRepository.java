package dartoo.accountService.repository;

import dartoo.accountService.domain.UserPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPlanRepository extends JpaRepository<UserPlan,Long> {
}
