package dartoo.accountService.config;

import dartoo.accountService.domain.UserAgreed;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPreference;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.repository.UserAgreedRepository;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.UserPreferenceRepository;
import dartoo.accountService.repository.core.UserCorpBookmarkRepository;
import dartoo.accountService.repository.core.UserNotificationRepository;
import dartoo.accountService.repository.core.UserPlanRepository;
import dartoo.accountService.repository.core.UserSearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalSeedConfig {
    private final PasswordEncoder passwordEncoder;

    //repository 목록
    private final UserEntityRepository userEntityRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserAgreedRepository userAgreedRepository;
    private final UserPlanRepository userPlanRepository;
    private final UserCorpBookmarkRepository userCorpBookmarkRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final UserSearchHistoryRepository userSearchHistoryRepository;

    @Transactional
    public void seed(){
        log.info("seeding local data.");
        //1) 사용자 계정 프로필
        var freeUser = createUser(
                "freeuser@gmail.com",
                "test1",
                "I'mFree",
                Role.USER,
                PlanType.FREE,
                PlanStatus.EXPIRED,
                null,
                Gender.MALE,
                LocalDate.of(2000,11,16)
        );
        var premiumUser = createUser(
                "premiumUser@gmail.com",
                "test2",
                "I'mPremium",
                Role.USER,
                PlanType.PREMIUM,
                PlanStatus.ACTIVE,
                Instant.now().plusSeconds(3600*30),
                Gender.FEMALE,
                LocalDate.of(2000,11,16)
        );
        setPreference(freeUser); setPreference(premiumUser);
        setAgreed(freeUser); setAgreed(premiumUser);
    }

    //사용자 더미데이터 생성
    private UserEntity createUser(
            String email,
            String rawPassword,
            String nickname,
            Role role,
            PlanType plan,
            PlanStatus planStatus,
            Instant planExpireAt,
            Gender gender,
            LocalDate birthday
    ) {
        return userEntityRepository.findByUserEmail(email)
                .orElseGet(() -> {
                    UserEntity user = UserEntity.builder()
                            .userEmail(email)
                            .password(passwordEncoder.encode(rawPassword))
                            .passwordSet(true)
                            .nickname(nickname)
                            .role(role)
                            .gender(gender)
                            .birthday(birthday)
                            .plan(plan)
                            .planStatus(planStatus)
                            .planExpireAt(planExpireAt)
                            .build();
                    return userEntityRepository.save(user);
                });
    }


    private void setAgreed(UserEntity user) {
        UserAgreed agreed = UserAgreed.builder()
                .user(user)
                .privacyAgreed(true)
                .privacyVersion("1.0.0")
                .tosAgreed(true)
                .tosVersion("1.0.0")
                .marketingAgreed(false)
                .build();
        user.attachAgreed(agreed);
        userAgreedRepository.save(agreed);
    }

    private void setPreference(UserEntity user) {
        UserPreference pref;
        if(user.getPlan()==PlanType.FREE){
            pref = UserPreference.builder()
                    .pushEnabled(true)
                    .emailEnabled(true)
                    .alertDelay(15)
                    .build();
        }
        else{
            pref = UserPreference.builder()
                    .pushEnabled(true)
                    .emailEnabled(false)
                    .alertDelay(1)
                    .build();
        }
        user.attachPreference(pref);
        userPreferenceRepository.save(pref);
    }

}
