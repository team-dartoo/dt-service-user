package dartoo.accountService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@EntityListeners(AuditingEntityListener.class)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String userEmail;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false)
    private String nickname;

    private LocalDate birthday;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    //두 테이블 모두 UserEntity 테이블과 생명주기가 동일하기 때문에 CascadeType.ALL 사용
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserPreference preference;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserAgreed agreed;


    public void changePassword(String newPassword) {
        this.password = newPassword;
    }

    public void changeProfile(String nickname, LocalDate birthday, Gender gender) {
        if (nickname != null && !nickname.isBlank()){
            this.nickname = nickname;
        }
        if (birthday != null && birthday.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생일은 미래일 수 없습니다.");
        }
        this.birthday = birthday;
        this.gender = gender;
    }

    public void attachPreference(UserPreference pref) {
        this.preference = pref;
        pref.setUser(this);
    }
    public void attachAgreed(UserAgreed agr) {
        this.agreed = agr;
        agr.setUser(this);
    }
}
