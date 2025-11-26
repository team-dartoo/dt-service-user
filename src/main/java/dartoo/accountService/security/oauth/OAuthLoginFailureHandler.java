package dartoo.accountService.security.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.error.ErrorResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.time.Instant;

/**
 * OAuth2 로그인 실패 시 호출되는 핸들러.
 *
 * 기본 Spring Security 동작은 실패 시 로그인 페이지로 리다이렉트하지만,
 * 여기선 JSON 응답(401)을 내려주고, 프런트에서 이 JSON을 보고 에러 화면 같은 것을 띄울 수 있도록 한다.
 * 문제 파악을 위해 warn 형태로 로그를 남기고, JSON도 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        log.warn("Oauth login failed: message={}, path={}", exception.getMessage(), request.getRequestURI());

        ErrorResult result = new ErrorResult(
                "OAUTH_LOGIN_FAILED",
                "OAuth 로그인 실패",
                HttpStatus.UNAUTHORIZED.value(),
                HtmlUtils.htmlEscape(request.getRequestURI()),
                Instant.now()
        );

        objectMapper.writeValue(
                response.getOutputStream(), result);
    }
}
