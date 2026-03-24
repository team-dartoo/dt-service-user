package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserSearchHistory;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.dto.core.SearchHistoryCreateRequest;
import dartoo.accountService.dto.core.SearchHistoryListResponse;
import dartoo.accountService.dto.core.SearchHistoryResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserSearchHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.HISTORY_NOT_FOUND;
import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceTest {

    @Mock
    UserSearchHistoryRepository userSearchHistoryRepository;
    @Mock
    UserEntityRepository userEntityRepository;

    @InjectMocks
    SearchHistoryService searchHistoryService;

    private UserEntity testUser;
    private Instant now = Instant.parse("2026-03-13T00:00:00Z");


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
                .build();

        mockSecurityContext("test@test.com");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("새로운 검색 기록 추가 성공")
    void addHistory_newSearch_success() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userSearchHistoryRepository.findByUser_IdAndQuery(1L, "테스트 검색어")).willReturn(Optional.empty());

        UserSearchHistory savedHistory = UserSearchHistory.builder()
                .id(1L)
                .user(testUser)
                .query("테스트 검색어")
                .searchedAt(now)
                .build();

        given(userSearchHistoryRepository.save(any(UserSearchHistory.class))).willReturn(savedHistory);
        given(userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(1L)).willReturn(List.of(savedHistory));

        SearchHistoryCreateRequest request = new SearchHistoryCreateRequest();
        request.setQuery("테스트 검색어");

        //when
        SearchHistoryResponse response = searchHistoryService.addHistory(request);

        //then
        assertThat(response.getHistoryId()).isEqualTo(1L);
        assertThat(response.getQuery()).isEqualTo("테스트 검색어");
        assertThat(response.getSearchedAt()).isNotNull();
        then(userSearchHistoryRepository).should().save(any(UserSearchHistory.class));
    }

    @Test
    @DisplayName("기존 검색 기록 업데이트 성공")
    void addHistory_updateExisting_success() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        Instant oldTime = Instant.now().minus(Duration.ofHours(1));
        UserSearchHistory existingHistory = UserSearchHistory.builder()
                .id(1L)
                .user(testUser)
                .query("테스트 검색어")
                .searchedAt(oldTime)
                .build();

        given(userSearchHistoryRepository.findByUser_IdAndQuery(1L, "테스트 검색어"))
                .willReturn(Optional.of(existingHistory));

        given(userSearchHistoryRepository.save(any(UserSearchHistory.class))).willAnswer(invocation -> {
            UserSearchHistory history = invocation.getArgument(0);
            return history;
        });

        given(userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(1L)).willReturn(List.of(existingHistory));

        SearchHistoryCreateRequest request = new SearchHistoryCreateRequest();
        request.setQuery("테스트 검색어");

        //when
        SearchHistoryResponse response = searchHistoryService.addHistory(request);

        //then
        assertThat(response.getHistoryId()).isEqualTo(1L);
        assertThat(response.getQuery()).isEqualTo("테스트 검색어");
        assertThat(response.getSearchedAt()).isAfter(oldTime);
        then(userSearchHistoryRepository).should().save(existingHistory);
    }

    @Test
    @DisplayName("검색 기록 추가 실패 - 사용자를 찾을 수 없음")
    void addHistory_userNotFound() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.empty());

        SearchHistoryCreateRequest request = new SearchHistoryCreateRequest();
        request.setQuery("테스트 검색어");

        //when, then
        assertThatThrownBy(() -> searchHistoryService.addHistory(request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);

        then(userSearchHistoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("검색 기록 전체 읽기 성공")
    void readHistory_success() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        UserSearchHistory history1 = UserSearchHistory.builder()
                .id(1L)
                .user(testUser)
                .query("검색어1")
                .searchedAt(now)
                .build();

        UserSearchHistory history2 = UserSearchHistory.builder()
                .id(2L)
                .user(testUser)
                .query("검색어2")
                .searchedAt(now.minus(Duration.ofMinutes(10)))
                .build();

        given(userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(1L))
                .willReturn(List.of(history1, history2));
        given(userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(eq(1L), any(PageRequest.class)))
                .willReturn(List.of(history1, history2));

        //when
        SearchHistoryListResponse response = searchHistoryService.readHistory(30);

        //then
        assertThat(response.getHistoryList()).hasSize(2);
        assertThat(response.getHistoryList().get(0).getQuery()).isEqualTo("검색어1");
        assertThat(response.getHistoryList().get(1).getQuery()).isEqualTo("검색어2");
    }

    @Test
    @DisplayName("검색 기록 읽기 성공 - limit 지정")
    void readHistory_withLimit_success() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        List<UserSearchHistory> histories = new ArrayList<>();
        //@param int limit의 값 입력 유무에 따른 테스트는 컨트롤러 단계에서 테스트
        for (int i = 0; i < 5; i++) {
            histories.add(UserSearchHistory.builder()
                    .id((long) i + 1)
                    .user(testUser)
                    .query("검색어" + (i + 1))
                    .searchedAt(now.minus(Duration.ofMinutes(i)))
                    .build());
        }

        given(userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(1L)).willReturn(histories);
        given(userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(1L, PageRequest.of(0, 3)))
                .willReturn(histories.subList(0, 3));

        //when
        SearchHistoryListResponse response = searchHistoryService.readHistory(3);

        //then
        assertThat(response.getHistoryList()).hasSize(3);
        then(userSearchHistoryRepository).should().findAllByUser_IdOrderBySearchedAtDesc(1L, PageRequest.of(0, 3));
    }

    @Test
    @DisplayName("검색 기록 읽기 실패 - 사용자를 찾을 수 없음")
    void readHistory_userNotFound() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.empty());

        //when, then
        assertThatThrownBy(() -> searchHistoryService.readHistory(30))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);
    }

    @Test
    @DisplayName("특정 검색 기록 삭제 성공")
    void deleteOne_success() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));

        UserSearchHistory history = UserSearchHistory.builder()
                .id(1L)
                .user(testUser)
                .query("삭제할 검색어")
                .searchedAt(now)
                .build();

        given(userSearchHistoryRepository.findByIdAndUser_Id(1L, 1L)).willReturn(Optional.of(history));

        //when
        searchHistoryService.deleteOne(1L);

        //then
        then(userSearchHistoryRepository).should().delete(history);
    }

    @Test
    @DisplayName("특정 검색 기록 삭제 실패 - 기록을 찾을 수 없음")
    void deleteOne_historyNotFound() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.of(testUser));
        given(userSearchHistoryRepository.findByIdAndUser_Id(999L, 1L)).willReturn(Optional.empty());

        //when, then
        assertThatThrownBy(() -> searchHistoryService.deleteOne(999L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", HISTORY_NOT_FOUND);

        then(userSearchHistoryRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("특정 검색 기록 삭제 실패 - 사용자를 찾을 수 없음")
    void deleteOne_userNotFound() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.empty());

        //when, then
        assertThatThrownBy(() -> searchHistoryService.deleteOne(1L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);

        then(userSearchHistoryRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("검색 기록 전체 삭제 성공")
    void deleteAll_success() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));

        // when
        searchHistoryService.deleteAll();

        // then (호출 잘 되는지만 검사, 실제로 삭제되는 지는 repository 단계에서 검사)
        then(userSearchHistoryRepository).should().deleteAllByUser_Id(1L);
    }

    @Test
    @DisplayName("검색 기록 전체 삭제 실패 - 사용자를 찾을 수 없음")
    void deleteAll_userNotFound() {
        //given
        given(userEntityRepository.findByUserEmail("test@test.com")).willReturn(Optional.empty());

        //when, then
        assertThatThrownBy(() -> searchHistoryService.deleteAll())
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", USER_NOT_FOUND);

        then(userSearchHistoryRepository).should(never()).deleteAllByUser_Id(any());
    }

    // cleanup() 메서드는 private이라 addHistory()를 통해 간접 호출
    // mockito 특성상 userSearchHistoryRepository.deleteBySearchedAtBefore()이 호출이 되었는지만 검증이 가능하고,
    // 실제 삭제가 되었는지는 service 단 테스트에서는 검증할 수 없기 때문에,
    // deleteAll의 호출 여부와, 실제 인수에 제대로 잘 들어갔는지 시간 정확성 검증만 수행하였다.
    @Test
    @DisplayName("cleanup - 90일 이전 검색 기록 삭제 (시간 정확성 검증)")
    void cleanup_deleteOldHistories_verifyDeadline() {
        // given
        // addHistory() 실행을 위한 최소한의 mock 세팅
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userSearchHistoryRepository.findByUser_IdAndQuery(1L, "새 검색어"))
                .willReturn(Optional.empty());
        given(userSearchHistoryRepository.save(any(UserSearchHistory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // 5개만 있다고 가정 (30개 미만 → 개수 초과 삭제는 발생하지 않음)
        // 목적: 90일 기준 시간 계산이 정확한지만 검증하기 위해 개수 삭제 케이스를 배제
        Instant now = Instant.now();
        List<UserSearchHistory> histories = createHistories(5, now);
        given(userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(1L))
                .willReturn(histories);

        SearchHistoryCreateRequest request = new SearchHistoryCreateRequest();
        request.setQuery("새 검색어");

        // when
        searchHistoryService.addHistory(request);

        // then
        // ArgumentCaptor: deleteBySearchedAtBefore()에 실제로 넘겨진 Instant 값을 캡처
        ArgumentCaptor<Instant> deadlineCaptor = ArgumentCaptor.forClass(Instant.class);
        then(userSearchHistoryRepository).should()
                .deleteBySearchedAtBefore(deadlineCaptor.capture());

        Instant capturedDeadline = deadlineCaptor.getValue();
        Instant expectedDeadline = now.minus(Duration.ofDays(90));

        // 실행 시점의 미세한 시간 차이를 허용하기 위해 1분 범위로 검증
        assertThat(capturedDeadline)
                .isAfter(expectedDeadline.minus(Duration.ofMinutes(1)))
                .isBefore(expectedDeadline.plus(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("cleanup - 1차 cleanup 후 남은 기록이 30개 초과 시, 기간 상관 없이 deleteAll 호출로 기록 삭제")
    void cleanup_deleteExcessHistories_31to30() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userSearchHistoryRepository.findByUser_IdAndQuery(1L, "새 검색어"))
                .willReturn(Optional.empty());
        given(userSearchHistoryRepository.save(any(UserSearchHistory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // 31개 존재 → 새 검색어 저장 후 cleanup() 시점에 90일 보다 이전에 검색된 검색기록 31개가 조회됨
        // createHistories()는 ID 1번(최신)~31번(가장 오래된) 순으로 생성
        List<UserSearchHistory> histories = createHistories(31, now);
        given(userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(1L))
                .willReturn(histories);

        SearchHistoryCreateRequest request = new SearchHistoryCreateRequest();
        request.setQuery("새 검색어");

        // when
        searchHistoryService.addHistory(request);

        // then
        // deleteAll()에 넘겨진 리스트를 argThat으로 직접 검증
        // subList(30, 31) → ID 31번 1개만 삭제되어야 함
        then(userSearchHistoryRepository).should().deleteAll(argThat(list -> {
            if (!(list instanceof List)) return false;
            List<?> deleteList = (List<?>) list;

            // 31 - 30 = 1개 삭제
            if (deleteList.size() != 1) return false;

            Object deleted = deleteList.get(0);
            if (!(deleted instanceof UserSearchHistory)) return false;

            UserSearchHistory deletedHistory = (UserSearchHistory) deleted;
            return deletedHistory.getId() == 31L; // 가장 오래된 것
        }));
    }

    @Test
    @DisplayName("cleanup - 1차 cleanup 후 남은 기록이 30개가 안될 경우 deleteAll 호출이 안됨")
    void cleanup_noDeleteWhenUnder30() {
        // given
        given(userEntityRepository.findByUserEmail("test@test.com"))
                .willReturn(Optional.of(testUser));
        given(userSearchHistoryRepository.findByUser_IdAndQuery(1L, "새 검색어"))
                .willReturn(Optional.empty());
        given(userSearchHistoryRepository.save(any(UserSearchHistory.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // 20개 → MAX(30개) 이하이므로 개수 기반 삭제(deleteAll)는 호출되면 안 됨
        List<UserSearchHistory> histories = createHistories(20, now);
        given(userSearchHistoryRepository.findAllByUser_IdOrderBySearchedAtDesc(1L))
                .willReturn(histories);

        SearchHistoryCreateRequest request = new SearchHistoryCreateRequest();
        request.setQuery("새 검색어");

        // when
        searchHistoryService.addHistory(request);

        // then
        // 90일 기준 삭제는 항상 실행, 개수 기반 삭제는 실행되면 안 됨
        then(userSearchHistoryRepository).should().deleteBySearchedAtBefore(any());
        then(userSearchHistoryRepository).should(never()).deleteAll(anyList());
    }

    // createHistories: ID = 1(최신) ~ count(가장 오래된) 순으로 생성
    // cleanup()의 subList 삭제 대상 ID를 예측하기 위해 이 순서가 중요함
    // 삭제 테스트용 검색 기록 생성 헬퍼 메서드
    private List<UserSearchHistory> createHistories(int count, Instant baseTime) {
        List<UserSearchHistory> histories = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            histories.add(UserSearchHistory.builder()
                    .id((long) (i + 1))
                    .user(testUser)
                    .query("검색어" + (i + 1))
                    .searchedAt(baseTime.minus(Duration.ofMinutes(i))) // i분 전
                    .build());
        }
        return histories; // 최신순 정렬
    }


    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        given(securityContext.getAuthentication()).willReturn(auth);
        given(auth.getName()).willReturn(email);

        SecurityContextHolder.setContext(securityContext);
    }
}
