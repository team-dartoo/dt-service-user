package dartoo.accountService.repository;

import dartoo.accountService.domain.Gender;
import dartoo.accountService.domain.Role;
import dartoo.accountService.domain.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
//기본적으로 H2 같은 인메모리 DB를 사용 + 각 테스트 후 DB 변경사항 자동 롤백
class UserEntityRepositoryTest {

    @Autowired
    UserEntityRepository userEntityRepository;
    @Autowired
    EntityManager entityManager;

    private UserEntity testUser1;
    private UserEntity testUser2;
    private UserEntity testUser3;

    @BeforeEach
    void setUp() {
        // given: 테스트용 사용자 데이터 준비
        testUser1 = UserEntity.builder()
                .userEmail("admin@test.com")
                .password("password123")
                .nickname("admin1")
                .birthday(LocalDate.of(2000,11,16))
                .role(Role.ADMIN)
                .gender(Gender.MALE)
                .build();

        testUser2 = UserEntity.builder()
                .userEmail("user2@test.com")
                .password("password456")
                .nickname("tester2")
                .birthday(LocalDate.of(1973, 4, 18))
                .role(Role.USER)
                .gender(Gender.FEMALE)
                .build();

        testUser3 = UserEntity.builder()
                .userEmail("user3@test.com")
                .password("password789")
                .nickname("tester3")
                .birthday(LocalDate.of(2009, 1, 6))
                .role(Role.USER)
                .gender(Gender.MALE)
                .build();

        // 데이터 저장 및 영속성 컨텍스트 반영
        entityManager.persist(testUser1);
        entityManager.persist(testUser2);
        entityManager.persist(testUser3);
        entityManager.flush();
        entityManager.clear(); // 1차 캐시 비우고 진짜 DB 조회처럼 검증
    }

    @DisplayName("이메일로 회원 조회하기")
    @Test
    void findByUserEmail() {
        //given
        String target = "admin@test.com";
        //when
        Optional<UserEntity> result = userEntityRepository.findByUserEmail(target);
        //then
        assertThat(result).isPresent();
        assertThat(result.get().getUserEmail()).isEqualTo(target);
        assertThat(result.get().getNickname()).isEqualTo("admin1");
        assertThat(result.get().getBirthday()).isEqualTo(LocalDate.of(2000,11,16));
        assertThat(result.get().getGender()).isEqualTo(Gender.MALE);
        assertThat(result.get().getRole()).isEqualTo(Role.ADMIN);
    }

    @DisplayName("기존에 존재하는 회원인지 조회하기")
    @Test
    void existsByUserEmail() {
        //given
        String trueTarget = "user3@test.com";
        String falseTarget = "nothing@no.com";
        //when
        boolean result1 = userEntityRepository.existsByUserEmail(trueTarget);
        boolean result2 = userEntityRepository.existsByUserEmail(falseTarget);
        //then
        assertThat(result1).isTrue();
        assertThat(result2).isFalse();
    }

    @DisplayName("이메일을 입력받아 회원정보가 정상적으로 삭제되었는지 검사하기")
    @Test
    void deleteByUserEmail() {
        //given
        String target = "user2@test.com";
        assertThat(userEntityRepository.existsByUserEmail(target)).isTrue();
        //when
        userEntityRepository.deleteByUserEmail(target);
        //then
        assertThat(userEntityRepository.existsByUserEmail(target)).isFalse();
    }

    @DisplayName("모든 사용자 조회하기")
    @Test
    void findAll(){
        //given - 이미 3개 저장됨
        //when
        List<UserEntity> result = userEntityRepository.findAll();
        //then
        assertThat(result).hasSize(3);
    }

    @DisplayName("사용자 정보 수정하기")
    @Test
    void updateUserEntity(){
        //given
        String target = "user3@test.com";
        UserEntity user = userEntityRepository.findByUserEmail(target).get();
        //when
        user.changeProfile("tester3_new", LocalDate.of(2009, 2, 27), Gender.FEMALE);
        userEntityRepository.flush();
        //then
        UserEntity newUser = userEntityRepository.findByUserEmail(target).get();
        assertThat(newUser.getNickname()).isEqualTo("tester3_new");
        assertThat(newUser.getBirthday()).isEqualTo(LocalDate.of(2009, 2, 27));
        assertThat(newUser.getGender()).isEqualTo(Gender.FEMALE);
    }
}