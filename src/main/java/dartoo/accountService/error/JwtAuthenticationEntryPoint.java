package dartoo.accountService.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        log.warn("Authentication failed: message={}, path={}", authException.getMessage(), request.getRequestURI());

        String userMessage = authException.getMessage() != null && authException.getMessage().toLowerCase().contains("expired")
                ? "액세스 토큰이 만료되었습니다."
                : "JWT 액세스 토큰 검증에 실패했습니다.";

        ErrorResult result = new ErrorResult(
                "AUTHENTICATION_FAILED",
                userMessage,
                HttpStatus.UNAUTHORIZED.value(),
                HtmlUtils.htmlEscape(request.getRequestURI()),
                Instant.now()
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        //json을 문자열로 변환해 body에 실어서 반환.
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
/*
JWT 인증 필터에서 발생하는 예외는 일반적인 @ExceptionHandler로는 잡을 수 없다.
스프링 시큐리티 인증 관련 예외인 AuthenticationException이 발생하면 이를 다루는 AuthenticationEntryPoint 클래스를 이용해
예외를 처리할 수 있다.
 */
