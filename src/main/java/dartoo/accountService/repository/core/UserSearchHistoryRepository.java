package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserSearchHistory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, Long> {
    Optional<UserSearchHistory> findByUserIdAndQuery(Long userId, String query);

    void deleteBySearchedAtBefore(Instant searchedAt);

    List<UserSearchHistory> findAllByUserIdOrderBySearchedAtDesc(Long userId, PageRequest pageRequest);

    List<UserSearchHistory> findAllByUserIdOrderBySearchedAtDesc(Long userId);

    Long deleteAllByUserId(Long userId);

    Optional<UserSearchHistory> findByIdAndUserId(Long id, Long userId);
}
