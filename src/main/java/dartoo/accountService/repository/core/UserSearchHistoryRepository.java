package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserSearchHistory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import javax.swing.text.html.Option;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, Long> {
    //사용자 ID와 검색어로 검색 기록 조회하기
    boolean existsByUser_Id(Long id);

    Optional<UserSearchHistory> findByUser_IdAndQuery(Long userId, String query);
    //검색 시각 이전의 검색 기록 삭제하기
    void deleteBySearchedAtBefore(Instant searchedAt);
    //사용자 ID로 검색 기록 조회하기 - 최신순 정렬
    List<UserSearchHistory> findAllByUser_IdOrderBySearchedAtDesc(Long userId, PageRequest pageRequest);
    //사용자 ID로 모든 검색 기록 삭제하기
    Long deleteAllByUser_Id(Long userId);
    //검색 기록 ID와 사용자 ID로 검색 기록 조회하기
    Optional<UserSearchHistory> findByIdAndUser_Id(Long id, Long userId);
    //사용자 ID로 모든 검색 기록 조회하기 - 최신순 정렬
    List<UserSearchHistory> findAllByUser_IdOrderBySearchedAtDesc(Long id);

}
