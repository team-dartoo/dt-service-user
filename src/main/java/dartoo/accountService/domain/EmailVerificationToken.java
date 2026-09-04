package dartoo.accountService.domain;

import dartoo.accountService.domain.enums.TokenPurpose;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenPurpose purpose;

    @Column(nullable = false)
    private boolean isUsed;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiredAt; //자연 만료 시각

    @Builder
    public EmailVerificationToken(String email, String token, TokenPurpose purpose, Instant expiredAt) {
        this.email = email;
        this.token = token;
        this.purpose = purpose;
        this.expiredAt = expiredAt;
        this.isUsed = false;
    }

    public void markAsUsed() {
        this.isUsed = true;
    }
}
