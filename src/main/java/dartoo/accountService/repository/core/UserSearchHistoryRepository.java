package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserSearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, Long> {
}
