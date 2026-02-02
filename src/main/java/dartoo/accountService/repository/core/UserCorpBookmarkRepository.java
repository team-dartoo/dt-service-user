package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserCorpBookmark;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCorpBookmarkRepository extends JpaRepository<UserCorpBookmark, Long> {
    List<UserCorpBookmark> findAllByUserIdOrderByCreatedAtDesc(Long id);

    boolean existsByUserIdAndCorpId(Long id, String corpId);

    long deleteByUserIdAndCorpId(Long id, String corpId);
}
