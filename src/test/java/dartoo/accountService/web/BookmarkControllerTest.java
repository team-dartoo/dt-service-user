package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.dto.core.BookmarkCreateRequest;
import dartoo.accountService.dto.core.BookmarkListResponse;
import dartoo.accountService.dto.core.BookmarkResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.CorpBookmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static dartoo.accountService.error.ErrorCode.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookmarkController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({BookmarkControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class BookmarkControllerTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockMvc mockMvc;

    @Autowired
    CorpBookmarkService bookmarkService;

    @TestConfiguration
    static class MockConfig {
        @Bean CorpBookmarkService corpBookmarkService() { return mock(CorpBookmarkService.class); }
    }

    @BeforeEach
    void setUp() {
        //호출 횟수 초기화
        reset(bookmarkService);
    }

    @Test
    @DisplayName("GET /api/users/bookmarks - 북마크 목록 조회 성공시 응답 코드 200")
    void getBookmarkListSuccess() throws Exception {
        //given
        BookmarkResponse bookmark1 = BookmarkResponse.builder()
                .corpCode("00001")
                .corpName("Test Corp 1")
                .createdAt(Instant.parse("2026-03-15T00:00:00Z"))
                .build();

        BookmarkResponse bookmark2 = BookmarkResponse.builder()
                .corpCode("00002")
                .corpName("Test Corp 2")
                .createdAt(Instant.parse("2026-03-16T00:00:00Z"))
                .build();

        BookmarkListResponse response = BookmarkListResponse.builder()
                .corpList(List.of(bookmark1, bookmark2))
                .build();

        given(bookmarkService.listCorpBookmark()).willReturn(response);
        //when, then
        mockMvc.perform(get("/api/users/bookmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corpList").isArray())
                .andExpect(jsonPath("$.corpList[0].corpCode").value("00001"))
                .andExpect(jsonPath("$.corpList[0].corpName").value("Test Corp 1"))
                .andExpect(jsonPath("$.corpList[0].createdAt").value("2026-03-15T00:00:00Z"))
                .andExpect(jsonPath("$.corpList[1].corpCode").value("00002"))
                .andExpect(jsonPath("$.corpList[1].corpName").value("Test Corp 2"))
                .andExpect(jsonPath("$.corpList[1].createdAt").value("2026-03-16T00:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/users/bookmarks - 빈 북마크 목록 조회 성공시 응답 코드 200")
    void getBookmarkListEmptySuccess() throws Exception {
        //given
        BookmarkListResponse response = BookmarkListResponse.builder()
                .corpList(List.of())
                .build();

        given(bookmarkService.listCorpBookmark()).willReturn(response);
        //when, then
        mockMvc.perform(get("/api/users/bookmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corpList").isArray())
                .andExpect(jsonPath("$.corpList").isEmpty());
    }

    @Test
    @DisplayName("POST /api/users/bookmarks - 북마크 추가 성공시 응답 코드 200")
    void addBookmarkSuccess() throws Exception {
        //given
        BookmarkCreateRequest request = new BookmarkCreateRequest();
        request.setCorpCode("00001");
        request.setCorpName("Test Corp");

        BookmarkResponse response = BookmarkResponse.builder()
                .corpCode("00001")
                .corpName("Test Corp")
                .createdAt(Instant.parse("2026-03-15T00:00:00Z"))
                .build();

        given(bookmarkService.addCorpBookmark(any(BookmarkCreateRequest.class))).willReturn(response);
        //when, then
        mockMvc.perform(post("/api/users/bookmarks")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corpCode").value("00001"))
                .andExpect(jsonPath("$.corpName").value("Test Corp"))
                .andExpect(jsonPath("$.createdAt").value("2026-03-15T00:00:00Z"));
    }

    @Test
    @DisplayName("POST /api/users/bookmarks - @Validated 오류 시 400 반환")
    void addBookmark_validationError() throws Exception {
        //given
        BookmarkCreateRequest request = new BookmarkCreateRequest();
        request.setCorpCode("");  // @NotBlank 위반
        request.setCorpName("Test Corp");

        //when, then
        mockMvc.perform(post("/api/users/bookmarks")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/bookmarks"));

        then(bookmarkService).should(never()).addCorpBookmark(any());
    }

    @Test
    @DisplayName("POST /api/users/bookmarks - JSON 형식 오류 시 400 반환")
    void addBookmark_invalidJsonFormat() throws Exception {
        //when, then
        mockMvc.perform(post("/api/users/bookmarks")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content("invalid json format"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/bookmarks"));

        then(bookmarkService).should(never()).addCorpBookmark(any());
    }

    @Test
    @DisplayName("POST /api/users/bookmarks - 중복된 북마크 추가 시도시 응답 코드 409 반환")
    void addBookmarkDuplicate() throws Exception {
        //given
        BookmarkCreateRequest request = new BookmarkCreateRequest();
        request.setCorpCode("00001");
        request.setCorpName("Test Corp");

        given(bookmarkService.addCorpBookmark(any(BookmarkCreateRequest.class)))
                .willThrow(new ApiException(DUPLICATE_BOOKMARK));
        //when, then
        mockMvc.perform(post("/api/users/bookmarks")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BOOKMARK"))
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.path").value("/api/users/bookmarks"));
    }

    @Test
    @DisplayName("POST /api/users/bookmarks - 사용자가 없을 경우 404 응답을 반환")
    void addBookmarkUserNotFound() throws Exception {
        //given
        BookmarkCreateRequest request = new BookmarkCreateRequest();
        request.setCorpCode("00001");
        request.setCorpName("Test Corp");

        given(bookmarkService.addCorpBookmark(any(BookmarkCreateRequest.class)))
                .willThrow(new ApiException(USER_NOT_FOUND));
        //when, then
        mockMvc.perform(post("/api/users/bookmarks")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/bookmarks"));
    }

    @Test
    @DisplayName("DELETE /api/users/bookmarks/{corpCode} - 북마크 삭제 성공시 응답 코드 204 반환")
    void deleteBookmarkSuccess() throws Exception {
        //given
        String corpCode = "00001";

        //deleteBookmark가 void를 반환하는 메서드라 형식이 다름
        willDoNothing().given(bookmarkService).deleteBookmark(eq(corpCode));
        //when, then
        mockMvc.perform(delete("/api/users/bookmarks/{corpCode}", corpCode))
                .andExpect(status().isNoContent());

        verify(bookmarkService, times(1)).deleteBookmark(eq(corpCode));
    }

    @Test
    @DisplayName("DELETE /api/users/bookmarks/{corpCode} - 존재하지 않는 북마크 삭제 시도시 응답 코드 404 반환")
    void deleteBookmarkNotFound() throws Exception {
        //given
        String corpCode = "99999";

        //deleteBookmark가 void를 반환하는 메서드라 형식이 다름
        willThrow(new ApiException(BOOKMARK_NOT_FOUND))
                .given(bookmarkService).deleteBookmark(eq(corpCode));
        //when, then
        mockMvc.perform(delete("/api/users/bookmarks/{corpCode}", corpCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKMARK_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/bookmarks/99999"));
    }

    @Test
    @DisplayName("DELETE /api/users/bookmarks/{corpCode} - 사용자가 없을 경우 404 응답을 반환")
    void deleteBookmarkUserNotFound() throws Exception {
        //given
        String corpCode = "00001";

        willThrow(new ApiException(USER_NOT_FOUND))
                .given(bookmarkService).deleteBookmark(eq(corpCode));
        //when, then
        mockMvc.perform(delete("/api/users/bookmarks/{corpCode}", corpCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/bookmarks/00001"));
    }
}
