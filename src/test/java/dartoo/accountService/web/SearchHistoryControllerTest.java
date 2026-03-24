package dartoo.accountService.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.dto.core.SearchHistoryCreateRequest;
import dartoo.accountService.dto.core.SearchHistoryListResponse;
import dartoo.accountService.dto.core.SearchHistoryResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.GlobalExceptionAdvice;
import dartoo.accountService.service.SearchHistoryService;
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
import java.util.Arrays;
import java.util.List;

import static dartoo.accountService.error.ErrorCode.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchHistoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SearchHistoryControllerTest.MockConfig.class, GlobalExceptionAdvice.class})
class SearchHistoryControllerTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MockMvc mockMvc;

    @Autowired
    SearchHistoryService searchHistoryService;

    @TestConfiguration
    static class MockConfig {
        @Bean SearchHistoryService searchHistoryService() { return mock(SearchHistoryService.class); }
    }

    @BeforeEach
    void setUp() {
        reset(searchHistoryService);
    }

    @Test
    @DisplayName("GET /api/users/search-histories - limit 파라미터 없을 때 검색 기록 조회 성공시 기본값 30으로 조회, 응답 코드 200")
    void getSearchHistoryListSuccess() throws Exception {
        //given
        Instant now = Instant.now();
        SearchHistoryResponse history1 = SearchHistoryResponse.builder()
                .historyId(1L)
                .query("삼성전자")
                .searchedAt(now)
                .build();
        SearchHistoryResponse history2 = SearchHistoryResponse.builder()
                .historyId(2L)
                .query("SK하이닉스")
                .searchedAt(now.minusSeconds(3600))
                .build();

        List<SearchHistoryResponse> historyList = Arrays.asList(history1, history2);
        SearchHistoryListResponse response = SearchHistoryListResponse.builder()
                .historyList(historyList)
                .build();

        given(searchHistoryService.readHistory(30)).willReturn(response);
        //when, then
        mockMvc.perform(get("/api/users/search-histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyList").isArray())
                .andExpect(jsonPath("$.historyList[0].historyId").value(1))
                .andExpect(jsonPath("$.historyList[0].query").value("삼성전자"))
                .andExpect(jsonPath("$.historyList[1].historyId").value(2))
                .andExpect(jsonPath("$.historyList[1].query").value("SK하이닉스"));
    }

    @Test
    @DisplayName("GET /api/users/search-histories - limit 파라미터로 조회 개수 제한 성공시 응답 코드 200")
    void getSearchHistoryListWithLimitSuccess() throws Exception {
        //given
        Instant now = Instant.now();
        SearchHistoryResponse history1 = SearchHistoryResponse.builder()
                .historyId(1L)
                .query("Test Query")
                .searchedAt(now)
                .build();

        List<SearchHistoryResponse> historyList = Arrays.asList(history1);
        SearchHistoryListResponse response = SearchHistoryListResponse.builder()
                .historyList(historyList)
                .build();

        given(searchHistoryService.readHistory(10)).willReturn(response);
        //when, then
        mockMvc.perform(get("/api/users/search-histories")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyList").isArray())
                .andExpect(jsonPath("$.historyList[0].historyId").value(1))
                .andExpect(jsonPath("$.historyList[0].query").value("Test Query"));
    }

    @Test
    @DisplayName("GET /api/users/search-histories - 사용자가 없을 경우 404 응답을 반환")
    void getSearchHistoryListUserNotFound() throws Exception {
        //given
        given(searchHistoryService.readHistory(30)).willThrow(new ApiException(USER_NOT_FOUND));
        //when,then
        mockMvc.perform(get("/api/users/search-histories"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/search-histories"));
    }

    @Test
    @DisplayName("POST /api/users/search-histories - 검색 기록 추가 성공시 응답 코드 200")
    void addSearchHistorySuccess() throws Exception {
        //given
        SearchHistoryCreateRequest request = new SearchHistoryCreateRequest();
        request.setQuery("New Search Query");

        Instant now = Instant.now();
        SearchHistoryResponse response = SearchHistoryResponse.builder()
                .historyId(1L)
                .query("New Search Query")
                .searchedAt(now)
                .build();

        given(searchHistoryService.addHistory(any(SearchHistoryCreateRequest.class))).willReturn(response);
        //when,then
        mockMvc.perform(post("/api/users/search-histories")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyId").value(1))
                .andExpect(jsonPath("$.query").value("New Search Query"))
                .andExpect(jsonPath("$.searchedAt").exists());
    }

    @Test
    @DisplayName("POST /api/users/search-histories - @Validated 오류 시 응답 코드 400 반환")
    void addSearchHistory_validationError() throws Exception {
        //given
        SearchHistoryCreateRequest request = new SearchHistoryCreateRequest();
        request.setQuery("");  // @NotBlank 위반

        //when, then
        mockMvc.perform(post("/api/users/search-histories")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/search-histories"));

        then(searchHistoryService).should(never()).addHistory(any());
    }

    @Test
    @DisplayName("POST /api/users/search-histories - JSON 형식 오류 시 응답 코드 400 반환")
    void addSearchHistory_invalidJsonFormat() throws Exception {
        //given
        // JSON 파싱 단계에서 실패하므로 서비스 mock 세팅 불필요

        //when, then
        mockMvc.perform(post("/api/users/search-histories")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content("invalid json format"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.path").value("/api/users/search-histories"));

        then(searchHistoryService).should(never()).addHistory(any());
    }

    @Test
    @DisplayName("POST /api/users/search-histories - 사용자가 없을 경우 404 응답을 반환")
    void addSearchHistoryUserNotFound() throws Exception {
        //given
        SearchHistoryCreateRequest request = new SearchHistoryCreateRequest();
        request.setQuery("Test Query");

        given(searchHistoryService.addHistory(any(SearchHistoryCreateRequest.class)))
                .willThrow(new ApiException(USER_NOT_FOUND));
        //when,then
        mockMvc.perform(post("/api/users/search-histories")
                        .contentType(APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/search-histories"));
    }

    @Test
    @DisplayName("DELETE /api/users/search-histories/{historyId} - 특정 검색 기록 삭제 성공시 응답 코드 204")
    void deleteOneSuccess() throws Exception {
        //given
        Long historyId = 1L;
        willDoNothing().given(searchHistoryService).deleteOne(historyId);
        //when, then
        mockMvc.perform(delete("/api/users/search-histories/{historyId}", historyId))
                .andExpect(status().isNoContent());

        then(searchHistoryService).should().deleteOne(eq(historyId));
    }

    @Test
    @DisplayName("DELETE /api/users/search-histories/{historyId} - 존재하지 않는 검색 기록 삭제 시 404 응답을 반환")
    void deleteOneHistoryNotFound() throws Exception {
        //given
        Long historyId = 999L;
        willThrow(new ApiException(HISTORY_NOT_FOUND)).given(searchHistoryService).deleteOne(historyId);
        //when,then
        mockMvc.perform(delete("/api/users/search-histories/{historyId}", historyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HISTORY_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/search-histories/999"));
    }

    @Test
    @DisplayName("DELETE /api/users/search-histories/{historyId} - 사용자가 없을 경우 404 응답을 반환")
    void deleteOneUserNotFound() throws Exception {
        //given
        Long historyId = 1L;
        willThrow(new ApiException(USER_NOT_FOUND)).given(searchHistoryService).deleteOne(historyId);
        //when,then
        mockMvc.perform(delete("/api/users/search-histories/{historyId}", historyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/search-histories/1"));
    }

    @Test
    @DisplayName("DELETE /api/users/search-histories - 모든 검색 기록 삭제 성공시 응답 코드 204")
    void deleteAllSuccess() throws Exception {
        //given
        willDoNothing().given(searchHistoryService).deleteAll();
        //when, then
        mockMvc.perform(delete("/api/users/search-histories"))
                .andExpect(status().isNoContent());

        verify(searchHistoryService, times(1)).deleteAll();
    }

    @Test
    @DisplayName("DELETE /api/users/search-histories - 사용자가 없을 경우 404 응답을 반환")
    void deleteAllUserNotFound() throws Exception {
        //given
        willThrow(new ApiException(USER_NOT_FOUND)).given(searchHistoryService).deleteAll();
        //when,then
        mockMvc.perform(delete("/api/users/search-histories"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.path").value("/api/users/search-histories"));
    }
}
