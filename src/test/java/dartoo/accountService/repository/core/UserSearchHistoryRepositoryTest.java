package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserSearchHistory;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.repository.UserEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserSearchHistoryRepositoryTest {

    @Autowired
    UserSearchHistoryRepository userSearchHistoryRepository;
    @Autowired
    UserEntityRepository userEntityRepository;
    @Autowired
    EntityManager entityManager;

    private UserEntity testUser;
    private UserSearchHistory history1;
    private UserSearchHistory history2;

    @BeforeEach
    void setUp() {
        // given: 테스트용 사용자 데이터 준비
        testUser = UserEntity.builder()
                .userEmail("test@test.com")
                .password("password123")
                .nickname("tester")
                .birthday(LocalDate.of(2000, 11, 16))
                .role(Role.USER)
                .gender(Gender.MALE)
                .build();

        entityManager.persist(testUser);

        Instant now = Instant.parse("2026-03-01T00:00:00Z");

        history1 = UserSearchHistory.builder()
                .user(testUser)
                .query("검색어1")
                .searchedAt(now.minus(1, ChronoUnit.HOURS))
                .build();

        history2 = UserSearchHistory.builder()
                .user(testUser)
                .query("검색어2")
                .searchedAt(now)
                .build();

        entityManager.persist(history1);
        entityManager.persist(history2);
        entityManager.flush();
        entityManager.clear();
    }

    @DisplayName("사용자 ID와 검색어로 검색 기록 조회하기")
    @Test
    void findByUser_IdAndQuery() {
        //when
        Optional<UserSearchHistory> result = userSearchHistoryRepository.findByUser_IdAndQuery(testUser.getId(), "검색어1");
        //then
        assertThat(result).isPresent();
        assertThat(result.get().getQuery()).isEqualTo("검색어1");
    }

    @DisplayName("검색 시각 이전의 검색 기록 삭제하기")
    @Test
    void deleteBySearchedAtBefore() {
        //given
        Instant deadline = history2.getSearchedAt().minus(30, ChronoUnit.MINUTES);
        //when
        userSearchHistoryRepository.deleteBySearchedAtBefore(deadline);
        entityManager.flush();
        entityManager.clear();
        //then
        List<UserSearchHistory> remaining = userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(testUser.getId());
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getQuery()).isEqualTo("검색어2");
    }

    @DisplayName("사용자 ID로 검색 기록 조회하기 - 최신순 정렬 및 페이징")
    @Test
    void findAllByUser_IdOrderBySearchedAtDescWithPaging() {
        //when
        List<UserSearchHistory> result = userSearchHistoryRepository
                .findAllByUser_IdOrderBySearchedAtDesc(testUser.getId(), PageRequest.of(0, 10));
        //then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getQuery()).isEqualTo("검색어2"); // 최신순
    }

    @DisplayName("사용자 ID로 모든 검색 기록 삭제하기")
    @Test
    void deleteAllByUser_Id() {
        //when
        Long deleted = userSearchHistoryRepository.deleteAllByUser_Id(testUser.getId());
        //then
        assertThat(deleted).isEqualTo(2);
        List<UserSearchHistory> remaining = userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(testUser.getId());
        assertThat(remaining).isEmpty();
    }

    @DisplayName("검색 기록 ID와 사용자 ID로 검색 기록 조회하기")
    @Test
    void findByIdAndUser_Id() {
        //when
        Optional<UserSearchHistory> result = userSearchHistoryRepository.findByIdAndUser_Id(history1.getId(), testUser.getId());
        //then
        assertThat(result).isPresent();
        assertThat(result.get().getQuery()).isEqualTo("검색어1");
    }

    @DisplayName("사용자 ID로 모든 검색 기록 조회하기 - 최신순 정렬")
    @Test
    void findAllByUser_IdOrderBySearchedAtDesc() {
        //when
        List<UserSearchHistory> result = userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(testUser.getId());
        //then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getQuery()).isEqualTo("검색어2"); //최신순 정렬
    }
}
