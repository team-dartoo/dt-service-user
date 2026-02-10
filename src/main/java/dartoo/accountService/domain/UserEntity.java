package dartoo.accountService.domain;

import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.error.ApiException;
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

import static dartoo.accountService.error.ErrorCode.USER_BIRTHDAY_CANNOT_BE_FUTURE;

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

    //처음에 소셜 로그인으로 회원 가입 시, 또는 비밀번호 재설정 시,
    //직접 비밀번호 초기화가 필요함을 알리기 위함
    @Column(nullable = false)
    @Builder.Default
    private Boolean passwordSet = false;

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

    //CoreService 구현하기 위해 추가된 속성 - 가장 최신 구독 정보를 User에 저장
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PlanType plan = PlanType.FREE;

    private Instant planExpireAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PlanStatus planStatus = PlanStatus.EXPIRED;

    //엔티티 비즈니스 메서드
    public void changePassword(String newPassword) {
        this.password = newPassword;
        this.passwordSet = true;
    }

    public void changeProfile(String nickname, LocalDate birthday, Gender gender) {
        if (nickname != null && !nickname.isBlank()){
            this.nickname = nickname;
        }
        if (birthday != null && birthday.isAfter(LocalDate.now())) {
            throw new ApiException(USER_BIRTHDAY_CANNOT_BE_FUTURE);
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

    //CoreService 구현 위해 추가된 메서드
    public void updatePlan(PlanType plan, PlanStatus status, Instant expireAt) {
        this.plan = plan;
        this.planStatus = status;
        this.planExpireAt = expireAt;
    }
}
