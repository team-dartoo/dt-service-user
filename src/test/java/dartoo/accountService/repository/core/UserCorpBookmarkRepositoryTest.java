package dartoo.accountService.repository.core;

import dartoo.accountService.domain.UserCorpBookmark;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.repository.UserEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserCorpBookmarkRepositoryTest {

    @Autowired
    UserCorpBookmarkRepository userCorpBookmarkRepository;
    @Autowired
    UserEntityRepository userEntityRepository;
    @Autowired
    EntityManager entityManager;

    private UserEntity testUser;
    private UserCorpBookmark bookmark1;
    private UserCorpBookmark bookmark2;

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

        bookmark1 = UserCorpBookmark.builder()
                .user(testUser)
                .corpCode("CORP001")
                .corpName("테스트 회사1")
                .build();

        bookmark2 = UserCorpBookmark.builder()
                .user(testUser)
                .corpCode("CORP002")
                .corpName("테스트 회사2")
                .build();

        entityManager.persist(bookmark1);
        entityManager.persist(bookmark2);
        entityManager.flush();
        entityManager.clear();
    }

    @DisplayName("사용자 ID로 모든 북마크 조회하기 - 최신순 정렬")
    @Test
    void findAllByUser_IdOrderByCreatedAtDesc() {
        //when
        List<UserCorpBookmark> result = userCorpBookmarkRepository.findAllByUser_IdOrderByCreatedAtDesc(testUser.getId());
        //then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCorpCode()).isIn("CORP001", "CORP002");
    }

    @DisplayName("사용자 ID와 기업 ID로 북마크 존재 여부 확인")
    @Test
    void existsByUser_IdAndCorpCode() {
        //when
        boolean exists = userCorpBookmarkRepository.existsByUser_IdAndCorpCode(testUser.getId(), "CORP001");
        boolean notExists = userCorpBookmarkRepository.existsByUser_IdAndCorpCode(testUser.getId(), "CORP999");
        //then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @DisplayName("사용자 ID와 기업 ID로 북마크 삭제하기")
    @Test
    void deleteByUser_IdAndCorpCode() {
        //given
        assertThat(userCorpBookmarkRepository.existsByUser_IdAndCorpCode(testUser.getId(), "CORP001")).isTrue();
        //when
        long deleted = userCorpBookmarkRepository.deleteByUser_IdAndCorpCode(testUser.getId(), "CORP001");
        //then
        assertThat(deleted).isEqualTo(1);
        assertThat(userCorpBookmarkRepository.existsByUser_IdAndCorpCode(testUser.getId(), "CORP001")).isFalse();
    }

    @DisplayName("사용자 ID로 모든 북마크 삭제하기")
    @Test
    void deleteAllByUser_Id() {
        //given
        assertThat(userCorpBookmarkRepository.findAllByUser_IdOrderByCreatedAtDesc(testUser.getId())).hasSize(2);
        //when
        userCorpBookmarkRepository.deleteAllByUser_Id(testUser.getId());
        //then
        assertThat(userCorpBookmarkRepository.findAllByUser_IdOrderByCreatedAtDesc(testUser.getId())).isEmpty();
    }
}
