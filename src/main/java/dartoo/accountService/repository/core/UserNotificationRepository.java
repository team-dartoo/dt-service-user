package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserNotification;
import dartoo.accountService.domain.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    Optional<UserNotification> findByIdAndUser_Id(Long id, Long userId);

    List<UserNotification> findAllByUser_IdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(Long userId, Instant createdAtAfter, NotificationStatus status);

    long deleteCreatedAtBefore(Instant deadline);

    //@Modifying -> 조회 허용
    //DB에 변경사항 전부 flush 후 캐시 비움
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update UserNotification n
           set n.status = :deletedStatus
         where n.user.id = :userId
           and n.createdAt >= :deadline
           and n.status <> :deletedStatus
    """)
    int softDeleteAllVisible(@Param("userId") Long userId,
                             @Param("deadline") Instant deadline,
                             @Param("deletedStatus") NotificationStatus deletedStatus);

}
