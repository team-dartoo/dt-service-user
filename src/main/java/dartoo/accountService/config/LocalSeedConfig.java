package dartoo.accountService.config;

import dartoo.accountService.domain.*;
import dartoo.accountService.domain.enums.*;
import dartoo.accountService.repository.UserAgreedRepository;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.UserPreferenceRepository;
import dartoo.accountService.repository.core.UserCorpBookmarkRepository;
import dartoo.accountService.repository.core.UserNotificationRepository;
import dartoo.accountService.repository.core.UserPlanRepository;
import dartoo.accountService.repository.core.UserSearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Configuration
@Profile("local")
@RequiredArgsConstructor
public class LocalSeedConfig {
    private static final String FREE_USER_EMAIL = "freeuser@gmail.com";
    private static final String FREE_USER_PASSWORD = "testuser1";
    private static final String PREMIUM_USER_EMAIL = "premiumUser@gmail.com";
    private static final String PREMIUM_USER_PASSWORD = "testuser2";

    private final PasswordEncoder passwordEncoder;

    //확실히 트랜잭션을 걸기 위해
    private final PlatformTransactionManager txManager;

    //repository 목록
    private final UserEntityRepository userEntityRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserAgreedRepository userAgreedRepository;
    private final UserPlanRepository userPlanRepository;
    private final UserCorpBookmarkRepository userCorpBookmarkRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final UserSearchHistoryRepository userSearchHistoryRepository;

    @Bean
    @Transactional
    ApplicationRunner localSeeder(){
        return args -> new TransactionTemplate(txManager).execute(status -> {
            seed(); // seed 전체가 하나의 트랜잭션 안에서 실행되도록 하기 위함
            return null;
        });
    }

    //사용자 더미 데이터와 설정 추가
    @Transactional
    public void seed(){
        log.info("더미 데이터 생성 중...");
        //1) 사용자 계정 프로필
        var freeUser = createUser(
                FREE_USER_EMAIL,
                FREE_USER_PASSWORD,
                "I'mFree",
                Role.USER,
                PlanType.FREE,
                PlanStatus.EXPIRED,
                null,
                Gender.MALE,
                LocalDate.of(2000,11,16)
        );
        var premiumUser = createUser(
                PREMIUM_USER_EMAIL,
                PREMIUM_USER_PASSWORD,
                "I'mPremium",
                Role.USER,
                PlanType.PREMIUM,
                PlanStatus.ACTIVE,
                Instant.now().plusSeconds(3600*24*30),
                Gender.FEMALE,
                LocalDate.of(2000,11,16)
        );
        setPreference(freeUser); setPreference(premiumUser);
        setAgreed(freeUser); setAgreed(premiumUser);

        setPlanHistory(freeUser); setPlanHistory(premiumUser);
        setBookmark(freeUser); setBookmark(premiumUser);
        setNotification(freeUser); setNotification(premiumUser);
        setSearchHistory(freeUser); setSearchHistory(premiumUser);
        log.info("더미데이터 생성 완료.\n무료 사용자 id = {}\n무료 사용자 비밀번호 = {}\n프리미엄 사용자 id = {}\n프리미엄 사용자 비밀번호 = {}\n"
        ,FREE_USER_EMAIL,FREE_USER_PASSWORD,PREMIUM_USER_EMAIL,PREMIUM_USER_PASSWORD);
    }

    private void setSearchHistory(UserEntity user) {
        UserSearchHistory searchHistory = UserSearchHistory.builder()
                .user(user)
                .searchedAt(Instant.now().minusSeconds(3600*24*3))
                .query("삼성전자")
                .build();

        UserSearchHistory searchHistory2 = UserSearchHistory.builder()
                .user(user)
                .searchedAt(Instant.now().minusSeconds(3600*24*2))
                .query("SK하이닉스")
                .build();
        userSearchHistoryRepository.save(searchHistory);
        userSearchHistoryRepository.save(searchHistory2);
    }

    private void setNotification(UserEntity user) {
        UserNotification notification = UserNotification.builder()
                .user(user)
                .title("멤버십 결제 만료")
                .content("35일 전에 결제한 멤버십 결제가 만료되었습니다.")
                .createdAt(Instant.now().minusSeconds(3600*24*5))
                .status(NotificationStatus.UNREAD)
                .readAt(null)
                .build();
        userNotificationRepository.save(notification);
    }

    private void setBookmark(UserEntity user) {
        UserCorpBookmark bookmark = UserCorpBookmark.builder()
                .user(user)
                .corpId("35161115")
                .corpName("삼성전자")
                .createdAt(Instant.now().minusSeconds(3600*24*3))
                .build();
        UserCorpBookmark bookmark2 = UserCorpBookmark.builder()
                .user(user)
                .corpId("64889445")
                .corpName("SK하이닉스")
                .createdAt(Instant.now().minusSeconds(3600*24*2))
                .build();
        userCorpBookmarkRepository.save(bookmark);
        userCorpBookmarkRepository.save(bookmark2);
    }

    private void setPlanHistory(UserEntity user) {
        UserPlan plan;
        if(user.getPlan()==PlanType.FREE){
            plan = UserPlan.builder()
                    .user(user)
                    .plan(PlanType.PREMIUM)
                    .status(PlanStatus.EXPIRED)
                    .startAt(Instant.now().minusSeconds(3600*24*35))
                    .expireAt(Instant.now().minusSeconds(3600*24*5))
                    .build();
        }
        else{
            plan = UserPlan.builder()
                    .user(user)
                    .plan(PlanType.PREMIUM)
                    .status(PlanStatus.ACTIVE)
                    .startAt(Instant.now().minusSeconds(3600*24*5))
                    .expireAt(Instant.now().plusSeconds(3600*24*25))
                    .build();
        }
        userPlanRepository.save(plan);
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
