package dartoo.accountService.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Setter
public class UserAgreed {
    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false)
    private Boolean tosAgreed;
    private String  tosVersion;
    @Column(nullable = false)
    private Boolean privacyAgreed;
    private String  privacyVersion;
    @Column(nullable = false)
    private Boolean marketingAgreed;
}
