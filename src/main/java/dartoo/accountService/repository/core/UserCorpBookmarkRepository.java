package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserCorpBookmark;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCorpBookmarkRepository extends JpaRepository<UserCorpBookmark, Long> {
    List<UserCorpBookmark> findAllByUser_IdOrderByCreatedAtDesc(Long id);

    boolean existsByUser_IdAndCorpId(Long id, String corpId);

    long deleteByUser_IdAndCorpId(Long id, String corpId);
}
