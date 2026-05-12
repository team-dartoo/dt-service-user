package dartoo.accountService.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.error.ErrorResult;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Component
public class ServiceApiKeyFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ServiceApiKeyFilter.class);
    private static final String HEADER_NAME = "X-Service-API-Key";

    @Value("${app.security.worker-api-key:}")
    private String expectedKey;

    private final ObjectMapper objectMapper;

    public ServiceApiKeyFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void logKeyStatus() {
        if (expectedKey == null || expectedKey.isBlank()) {
            log.warn("app.security.worker-api-key is blank — all internal API requests will be rejected. "
                    + "Set WORKER_API_KEY to a non-empty value.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (expectedKey == null || expectedKey.isBlank()) {
            reject(request, response);
            return;
        }
        String provided = request.getHeader(HEADER_NAME);
        if (provided == null || !constantTimeEquals(expectedKey, provided)) {
            reject(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("Invalid or missing service API key: method={}, path={}, remoteAddr={}",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr());

        ErrorResult result = new ErrorResult(
                "INVALID_SERVICE_API_KEY",
                "유효하지 않은 서비스 API 키입니다.",
                HttpServletResponse.SC_UNAUTHORIZED,
                HtmlUtils.htmlEscape(request.getRequestURI()),
                Instant.now()
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    /**
     * Constant-time comparison to mitigate timing attacks.
     * Returns true only when both byte sequences have the same length and content.
     */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
