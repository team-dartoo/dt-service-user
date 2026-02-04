package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPlanRepository extends JpaRepository<UserPlan,Long> {
    List<UserPlan> findAllByUserIdOrderByStartAtDesc(Long id);
}
