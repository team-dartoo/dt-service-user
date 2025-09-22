package dartoo.accountService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity userEntity;

    private String token; //해시된 채로 들어가 있음.

    private Instant createdAt;
    private Instant expiredAt; //자연 만료
    private Instant revokedAt; //폐기
    private Instant rotatedAt; //단순 회전

    private String did; //device_id, uuid
    private String userAgent;

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    public void rotate(Instant now) {
        this.rotatedAt = now;
    }
}
