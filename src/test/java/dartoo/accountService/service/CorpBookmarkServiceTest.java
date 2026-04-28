package dartoo.accountService.service;

import dartoo.accountService.domain.UserCorpBookmark;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.dto.core.BookmarkCreateRequest;
import dartoo.accountService.dto.core.BookmarkListResponse;
import dartoo.accountService.dto.core.BookmarkResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserCorpBookmarkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CorpBookmarkServiceTest {

    @Mock
    UserCorpBookmarkRepository userCorpBookmarkRepository;
    @Mock
    UserEntityRepository userEntityRepository;

    @InjectMocks
    CorpBookmarkService corpBookmarkService;

    private UserEntity testUser;

    final Instant now = Instant.parse("2026-03-11T10:00:00Z");

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id(1L)
                .userEmail("test@test.com")
                .password("encodedPassword")
                .nickname("테스터")
                .role(Role.USER)
                .gender(Gender.MALE)
                .birthday(LocalDate.of(2000, 11, 16))
                .createdAt(now.minus(50, ChronoUnit.DAYS))
                .build();

        mockSecurityContext("test@test.com");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("북마크 목록 조회 성공")
    void listCorpBookmark_success() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        UserCorpBookmark bookmark1 = UserCorpBookmark.builder()
                .user(testUser)
                .corpCode("CORP001")
                .corpName("테스트 회사1")
                .displayOrder(1)
                .createdAt(now.minus(10, ChronoUnit.DAYS))
                .build();

        UserCorpBookmark bookmark2 = UserCorpBookmark.builder()
                .user(testUser)
                .corpCode("CORP002")
                .corpName("테스트 회사2")
                .displayOrder(0)
                .createdAt(now.minus(5, ChronoUnit.DAYS))
                .build();

        given(userCorpBookmarkRepository.findAllByUser_IdOrderByDisplayOrderAscIdAsc(1L))
                .willReturn(List.of(bookmark2, bookmark1));

        //when
        BookmarkListResponse response = corpBookmarkService.listCorpBookmark();

        //then
        assertThat(response.getCorpList()).hasSize(2);
        assertThat(response.getCorpList().get(0).getCorpCode()).isEqualTo("CORP002");
    }

    @Test
    @DisplayName("북마크 추가 성공")
    void addCorpBookmark_success() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userCorpBookmarkRepository.existsByUser_IdAndCorpCode(1L, "CORP001")).willReturn(false);

        UserCorpBookmark savedBookmark = UserCorpBookmark.builder()
                .user(testUser)
                .corpCode("CORP001")
                .corpName("테스트 회사1")
                .build();

        given(userCorpBookmarkRepository.save(any(UserCorpBookmark.class))).willReturn(savedBookmark);

        BookmarkCreateRequest request = new BookmarkCreateRequest();
        request.setCorpCode("CORP001");
        request.setCorpName("테스트 회사1");

        //when
        BookmarkResponse response = corpBookmarkService.addCorpBookmark(request);

        //then
        assertThat(response.getCorpCode()).isEqualTo("CORP001");
        assertThat(response.getCorpName()).isEqualTo("테스트 회사1");
        then(userCorpBookmarkRepository).should().save(any(UserCorpBookmark.class));
    }

    @Test
    @DisplayName("북마크 추가 실패 - 중복된 북마크")
    void addCorpBookmark_duplicate() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userCorpBookmarkRepository.existsByUser_IdAndCorpCode(1L, "CORP001")).willReturn(true);

        BookmarkCreateRequest request = new BookmarkCreateRequest();
        request.setCorpCode("CORP001");
        request.setCorpName("테스트 회사1");

        //when, then
        assertThatThrownBy(() -> corpBookmarkService.addCorpBookmark(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", DUPLICATE_BOOKMARK);

        then(userCorpBookmarkRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("북마크 삭제 성공")
    void deleteBookmark_success() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userCorpBookmarkRepository.deleteByUser_IdAndCorpCode(1L, "CORP001")).willReturn(1L);

        //when
        corpBookmarkService.deleteBookmark("CORP001");

        //then (해당 메서드가 제대로 호출되었는지 검증)
        then(userCorpBookmarkRepository).should().deleteByUser_IdAndCorpCode(1L, "CORP001");
    }

    @Test
    @DisplayName("북마크 삭제 실패 - BOOKMARK_NOT_FOUND")
    void deleteBookmark_notFound() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userCorpBookmarkRepository.deleteByUser_IdAndCorpCode(1L, "CORP999")).willReturn(0L);

        //when, then
        assertThatThrownBy(() -> corpBookmarkService.deleteBookmark("CORP999"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", BOOKMARK_NOT_FOUND);
    }

    @Test
    @DisplayName("사용자 조회 실패 - USER_NOT_FOUND")
    void getCurrentUser_fail() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.empty());

        //when, then
        assertThatThrownBy(() -> corpBookmarkService.listCorpBookmark())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        given(securityContext.getAuthentication()).willReturn(auth);
        given(auth.getName()).willReturn(email);

        SecurityContextHolder.setContext(securityContext);
    }
}
