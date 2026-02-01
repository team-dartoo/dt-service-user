package dartoo.accountService.domain;

import dartoo.accountService.domain.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_notification",
        indexes = {
                @Index(name = "idx_user_notif_user_created", columnList = "user_id, created_at DESC")
        }
)
public class UserNotification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id")
    private UserEntity user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.UNREAD;

    @CreatedDate
    private Instant createdAt;

    private Instant readAt;

    public void markRead(Instant now) {
        if (this.status == NotificationStatus.UNREAD) {
            this.status = NotificationStatus.READ;
            this.readAt = now;
        }
    }

    public void markDeleted() {
        this.status = NotificationStatus.DELETED;
    }
}
