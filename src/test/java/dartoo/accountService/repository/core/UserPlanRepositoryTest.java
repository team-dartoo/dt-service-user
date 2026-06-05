package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPlan;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.dto.core.enums.PlanDuration;
import dartoo.accountService.repository.UserEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserPlanRepositoryTest {

    @Autowired
    UserPlanRepository userPlanRepository;
    @Autowired
    UserEntityRepository userEntityRepository;
    @Autowired
    EntityManager entityManager;

    private UserEntity testUser;
    private UserPlan plan1;
    private UserPlan plan2;

    // 데이터 생성 기준 시각
    private static final Instant BASE_TIME = Instant.parse("2026-03-11T10:00:00Z");
    // 실제 쿼리 시각 - 데이터 생성 후 몇 분이 지난 시점을 현실적으로 반영
    private static final Instant QUERY_TIME = BASE_TIME.plus(5, ChronoUnit.MINUTES);

    @BeforeEach
    void setUp() {
        // given: 테스트용 사용자 데이터 준비
        Instant now = BASE_TIME;

        testUser = UserEntity.builder()
                .userEmail("test@test.com")
                .password("password123")
                .nickname("tester")
                .birthday(LocalDate.of(2000, 11, 16))
                .role(Role.USER)
                .gender(Gender.MALE)
                .createdAt(now.minus(500, ChronoUnit.DAYS))
                .build();

        entityManager.persist(testUser);

        plan1 = UserPlan.builder()
                .user(testUser)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .status(PlanStatus.ACTIVE)
                .startAt(now.minus(10, ChronoUnit.DAYS))
                .expireAt(now.plus(20, ChronoUnit.DAYS))
                .createdAt(now.minus(10, ChronoUnit.DAYS))
                .transactionId("tx_plan1")   // 추가
                .store("PLAY_STORE")          // 추가
                .build();

        plan2 = UserPlan.builder()
                .user(testUser)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.YEARLY)
                .status(PlanStatus.EXPIRED)
                .startAt(now.minus(400, ChronoUnit.DAYS))
                .expireAt(now.minus(35, ChronoUnit.DAYS))
                .createdAt(now.minus(400, ChronoUnit.DAYS))
                .transactionId("tx_plan2")   // 추가
                .store("APP_STORE")           // 추가
                .build();

        entityManager.persist(plan1);
        entityManager.persist(plan2);
        entityManager.flush();
        entityManager.clear();
    }

    @DisplayName("사용자 ID로 모든 플랜 조회하기 - 만료일 내림차순 정렬")
    @Test
    void findAllByUser_IdOrderByStartAtDesc() {
        //given
        Long userId = testUser.getId();
        //when
        List<UserPlan> result = userPlanRepository.findAllByUser_IdOrderByStartAtDesc(userId);
        //then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserPlan::getStatus)
                .containsExactlyInAnyOrder(PlanStatus.ACTIVE, PlanStatus.EXPIRED);
    }

    @DisplayName("현재 유효한 플랜 조회하기")
    @Test
    void findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc() {
        //when
        Optional<UserPlan> result = userPlanRepository
                .findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                        testUser.getId(), QUERY_TIME, QUERY_TIME, List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED)
                );
        //then
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(result.get().getDuration()).isEqualTo(PlanDuration.MONTHLY);
    }

    @DisplayName("미래 연장분 플랜 조회하기")
    @Test
    void findAllByUser_IdAndStartAtGreaterThanEqualAndStatusOrderByStartAtAsc() {
        //given
        Instant currentExpireAt = plan1.getExpireAt();
        UserPlan futurePlan = UserPlan.builder()
                .user(testUser)
                .plan(PlanType.PREMIUM)
                .duration(PlanDuration.MONTHLY)
                .status(PlanStatus.ACTIVE)
                .startAt(currentExpireAt)
                .expireAt(currentExpireAt.plus(30, ChronoUnit.DAYS))
                .transactionId("tx_future")  // 추가
                .store("PLAY_STORE")          // 추가
                .build();
        entityManager.persist(futurePlan);
        entityManager.flush();
        //when
        List<UserPlan> result = userPlanRepository.findAllByUser_IdAndStartAtGreaterThanEqualAndStatusOrderByStartAtAsc(
                testUser.getId(), currentExpireAt, PlanStatus.ACTIVE
        );
        //then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStartAt()).isEqualTo(currentExpireAt);
    }

    @DisplayName("미래 연장분 플랜 존재 여부 확인하기")
    @Test
    void existsByUser_IdAndStartAtGreaterThanEqualAndStatus() {
        //given
        Long userId = testUser.getId();
        Instant currentExpireAt = QUERY_TIME.plus(20, ChronoUnit.DAYS);
        //when
        boolean exists = userPlanRepository.existsByUser_IdAndStartAtGreaterThanEqualAndStatus(
                userId, currentExpireAt, PlanStatus.ACTIVE
        );
        //then
        assertThat(exists).isFalse();
    }

    @DisplayName("만료된 플랜 조회하기")
    @Test
    void findAllByExpireAtBeforeAndStatusIn() {
        //when
        List<UserPlan> result = userPlanRepository.findAllByExpireAtBeforeAndStatusIn(
                QUERY_TIME, List.of(PlanStatus.ACTIVE)
        );
        //then
        assertThat(result).isEmpty();
    }

    @DisplayName("특정 사용자들의 활성 플랜 조회하기")
    @Test
    void findAllActivePlansForUsers() {
        //when
        List<UserPlan> result = userPlanRepository.findAllActivePlansForUsers(
                List.of(testUser.getId()), QUERY_TIME, PlanStatus.ACTIVE
        );
        //then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(PlanStatus.ACTIVE);
    }

    @DisplayName("사용자가 특정 기간의 플랜을 가지고 있는지 확인하기")
    @Test
    void existsByUser_IdAndDuration() {
        //when
        boolean hasMonthly = userPlanRepository.existsByUser_IdAndDuration(testUser.getId(), PlanDuration.MONTHLY);
        boolean hasTrial = userPlanRepository.existsByUser_IdAndDuration(testUser.getId(), PlanDuration.TRIAL);
        //then
        assertThat(hasMonthly).isTrue();
        assertThat(hasTrial).isFalse();
    }

    @DisplayName("사용자가 특정 기간들 중 하나의 플랜을 가지고 있는지 확인하기")
    @Test
    void existsByUser_IdAndDurationIn() {
        //when
        boolean hasPaid = userPlanRepository.existsByUser_IdAndDurationIn(
                testUser.getId(), List.of(PlanDuration.MONTHLY, PlanDuration.YEARLY)
        );
        //then
        assertThat(hasPaid).isTrue();
    }


    @DisplayName("만료되지 않은 ACTIVE 플랜 전체 조회")
    @Test
    void findAllByUser_IdAndExpireAtAfterAndStatus() {
        //when
        List<UserPlan> result = userPlanRepository.findAllByUser_IdAndExpireAtAfterAndStatus(
                testUser.getId(), QUERY_TIME, PlanStatus.ACTIVE
        );
        //then
        // plan1(ACTIVE, 만료 안됨)만 조회, plan2(EXPIRED)는 제외
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTransactionId()).isEqualTo("tx_plan1");
    }

    @DisplayName("transactionId와 상태로 플랜 조회하기")
    @Test
    void findTopByUser_IdAndTransactionIdAndStatusInOrderByExpireAtDesc() {
        //when
        Optional<UserPlan> result = userPlanRepository.findTopByUser_IdAndTransactionIdAndStatusInOrderByExpireAtDesc(
                testUser.getId(), "tx_plan2", List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED, PlanStatus.EXPIRED)
        );
        //then
        assertThat(result).isPresent();
        assertThat(result.get().getTransactionId()).isEqualTo("tx_plan2");
        assertThat(result.get().getStatus()).isEqualTo(PlanStatus.EXPIRED);
    }
}
