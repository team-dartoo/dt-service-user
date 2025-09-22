package dartoo.accountService.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Setter
public class UserPreference {
    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    private Boolean pushEnabled;
    private Boolean emailEnabled;
    private Integer alertDelay;

    @LastModifiedDate
    private Instant updatedAt;
}
