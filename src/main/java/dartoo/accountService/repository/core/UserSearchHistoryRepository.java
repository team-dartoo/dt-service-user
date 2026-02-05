package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserSearchHistory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import javax.swing.text.html.Option;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, Long> {
    Optional<UserSearchHistory> findByUser_IdAndQuery(Long userId, String query);

    void deleteBySearchedAtBefore(Instant searchedAt);

    List<UserSearchHistory> findAllByUser_IdOrderBySearchedAtDesc(Long userId, PageRequest pageRequest);

    Long deleteAllByUser_Id(Long userId);

    Optional<UserSearchHistory> findByIdAndUser_Id(Long id, Long userId);

    List<UserSearchHistory> findAllByUser_IdOrderBySearchedAtDesc(Long id);

}
