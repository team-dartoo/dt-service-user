package dartoo.accountService.security.oauth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 로그인 실패 시 호출되는 핸들러.
 *
 * OAuth는 브라우저 리다이렉트 기반이므로 JSON 응답을 내려줘도 React에서 받을 수 없고,
 * 브라우저가 JSON 텍스트를 그대로 화면에 표시하게 된다.
 * 따라서 실패 시 프론트 로그인 페이지로 리다이렉트하고, query param으로 에러 원인을 전달한다.
 * 문제 파악을 위해 warn 형태로 로그를 남긴다.
 */
@Slf4j
@Component
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        log.warn("Oauth login failed: message={}, path={}", exception.getMessage(), request.getRequestURI());

        // ErrorResult result = new ErrorResult(
        //         "OAUTH_LOGIN_FAILED",
        //         "OAuth 로그인 실패",
        //         HttpStatus.UNAUTHORIZED.value(),
        //         HtmlUtils.htmlEscape(request.getRequestURI()),
        //         Instant.now()
        // );
        // objectMapper.writeValue(response.getOutputStream(), result);

        //OAuth는 브라우저 리다이렉트 기반이므로 에러 발생 시 프론트 로그인 페이지로 리다이렉트
        response.sendRedirect(frontendUrl + "/login?error=oauth_failed");
    }
}
