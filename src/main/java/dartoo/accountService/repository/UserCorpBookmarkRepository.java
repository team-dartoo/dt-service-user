package dartoo.accountService.repository;

import dartoo.accountService.domain.UserCorpBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCorpBookmarkRepository extends JpaRepository<UserCorpBookmark, Long> {
}
