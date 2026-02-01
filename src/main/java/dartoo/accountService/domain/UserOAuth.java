package dartoo.accountService.domain;

import dartoo.accountService.domain.enums.SocialProvider;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserOAuth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="userEntity_id")
    private UserEntity userEntity;

    @Enumerated(EnumType.STRING)
    private SocialProvider provider;

    @Column(unique = true)
    private String providerUserId;

    private String providerEmail;

    @CreatedDate
    private Instant linkedAt;

    public void updateProviderEmail(String email) {
        this.providerEmail = email;
    }
}
