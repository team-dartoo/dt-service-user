package dartoo.accountService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
//중복 북마크 방지용 CONSTRAINT, 빠른 검색용 INDEX
@Table(name = "user_corp_bookmark",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_bookmark_user_corp", columnNames = {"user_id", "corp_code"})
        },
        indexes = {
                @Index(name = "idx_user_bookmark_user_created", columnList = "user_id, created_at DESC"),
                @Index(name = "idx_user_bookmark_user_order", columnList = "user_id, display_order ASC")
        }
)
public class UserCorpBookmark {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="user_id", nullable = false)
    private UserEntity user;

    @Column(name = "corp_code", nullable = false)
    private String corpCode;

    private String corpName;

    @Column(name = "display_order")
    private Integer displayOrder;

    @CreatedDate
    private Instant createdAt;

    public void updateDisplayOrder(int order) {
        this.displayOrder = order;
    }
}
