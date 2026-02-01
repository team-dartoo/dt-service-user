package dartoo.accountService.repository;

import dartoo.accountService.domain.UserSearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, Long> {
}
