package dartoo.accountService.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ServiceApiKeyFilterTest {

    private ServiceApiKeyFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        filter = new ServiceApiKeyFilter(new ObjectMapper().findAndRegisterModules());
        chain = mock(FilterChain.class);
        setExpectedKey("valid-worker-key");
    }

    private void setExpectedKey(String key) throws Exception {
        Field field = ServiceApiKeyFilter.class.getDeclaredField("expectedKey");
        field.setAccessible(true);
        field.set(filter, key);
    }

    private MockHttpServletRequest internalRequest() {
        return new MockHttpServletRequest("POST", "/internal/api/notifications/bulk");
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }

    @Test
    @DisplayName("올바른 API 키 → 체인 통과")
    void validKey_passes() throws ServletException, IOException {
        MockHttpServletRequest request = internalRequest();
        request.addHeader("X-Service-API-Key", "valid-worker-key");
        MockHttpServletResponse response = response();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("헤더 누락 → 401")
    void missingHeader_rejects() throws ServletException, IOException {
        MockHttpServletResponse response = response();

        filter.doFilter(internalRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("잘못된 API 키 → 401")
    void wrongKey_rejects() throws ServletException, IOException {
        MockHttpServletRequest request = internalRequest();
        request.addHeader("X-Service-API-Key", "wrong-key");
        MockHttpServletResponse response = response();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("expectedKey가 빈 문자열 → 모든 요청 거부 (fail-closed)")
    void blankExpectedKey_rejectsAll() throws Exception {
        setExpectedKey("");
        MockHttpServletRequest request = internalRequest();
        request.addHeader("X-Service-API-Key", "");
        MockHttpServletResponse response = response();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("expectedKey가 null → 모든 요청 거부 (fail-closed)")
    void nullExpectedKey_rejectsAll() throws Exception {
        setExpectedKey(null);
        MockHttpServletRequest request = internalRequest();
        request.addHeader("X-Service-API-Key", "anything");
        MockHttpServletResponse response = response();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("expectedKey가 공백만 있음 → 모든 요청 거부 (fail-closed)")
    void whitespaceOnlyExpectedKey_rejectsAll() throws Exception {
        setExpectedKey("   ");
        MockHttpServletRequest request = internalRequest();
        request.addHeader("X-Service-API-Key", "   ");
        MockHttpServletResponse response = response();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("/internal/api/ 외 경로 → 필터 스킵, 체인 통과")
    void nonInternalPath_skipsFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/info");
        MockHttpServletResponse response = response();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
