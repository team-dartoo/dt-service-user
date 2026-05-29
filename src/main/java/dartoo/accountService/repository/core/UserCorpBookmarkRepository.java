package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserCorpBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCorpBookmarkRepository extends JpaRepository<UserCorpBookmark, Long> {
    List<UserCorpBookmark> findAllByUser_IdOrderByCreatedAtDesc(Long id);

    List<UserCorpBookmark> findAllByUser_IdOrderByDisplayOrderAscIdAsc(Long id);

    boolean existsByUser_Id(Long id);

    boolean existsByUser_IdAndCorpCode(Long id, String corpCode);

    long deleteByUser_IdAndCorpCode(Long id, String corpCode);

    void deleteAllByUser_Id(Long id);

    List<UserCorpBookmark> findAllByUser_IdAndDisplayOrderIsNullOrderByCreatedAtDescIdAsc(Long id);

    @Query("SELECT COALESCE(MAX(b.displayOrder), -1) FROM UserCorpBookmark b WHERE b.user.id = :userId")
    Integer findMaxDisplayOrderByUser_Id(@Param("userId") Long userId);

    List<UserCorpBookmark> findAllByCorpCode(String corpCode);
}
