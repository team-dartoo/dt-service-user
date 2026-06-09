package dartoo.accountService.domain;

import dartoo.accountService.domain.converter.StringListJsonConverter;
import dartoo.accountService.domain.enums.NotificationStatus;
import dartoo.accountService.domain.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;

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
@Entity
public class UserNotification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id")
    private UserEntity user;

    @Column(nullable = false)
    private String title;

    @Column(name = "recept_no", length = 14)
    private String receptNo;

    @Column(name = "corp_name", length = 200)
    private String corpName;

    @Column(name = "corp_code", length = 8)
    private String corpCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 32, nullable = false)
    private NotificationType eventType;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "summary_lines", columnDefinition = "TEXT")
    private List<String> summaryLines;

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
