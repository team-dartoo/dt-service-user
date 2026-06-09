package dartoo.accountService.security.oauth;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.dto.account.TokenResponseDto;
import dartoo.accountService.dto.oauth.SocialLoginResultDto;
import dartoo.accountService.error.ApiException;
import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.security.oauth.userInfo.FetchGoogleOAuthUserInfo;
import dartoo.accountService.security.oauth.userInfo.FetchKakaoOAuthUserInfo;
import dartoo.accountService.security.oauth.userInfo.FetchNaverOAuthUserInfo;
import dartoo.accountService.security.oauth.userInfo.FetchOAuthUserInfo;
import dartoo.accountService.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 로그인 성공 이후 후처리를 담당하는 핸들러.
 *
 * Spring Security가
 *  - /oauth2/authorization/{provider} 진입 → provider 로그인 페이지로 리다이렉트
 *  - provider가 redirect_uri로 code 전달 → 토큰 교환 + 사용자 프로필 조회
 * 까지를 자동으로 수행한 뒤,
 * 여기에서:
 *  - provider별 사용자 프로필(attributes)을 공통 OAuth2UserInfo로 변환하고 UserEntity와 소셜 계정 정보를 upsert한 다음,
 *  - 최종적으로 JWT를 발급해서 프론트엔드 콜백 URL로 리다이렉트한다.
 *  - 프런트와 백엔드가 동일 도메인을 사용하므로 절대 경로 리다이렉트로 토큰을 전달한다.
 *  - 마이페이지에서 계정 연결을 누른 경우에도, 로그인으로 리다이렉트 된 뒤에 인증정보를 전달해주는 것이기 때문에,
 *  - 여기서 처리해서 결과를 리다이렉트 형태로 전달하게 된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final SocialLoginService socialLoginService;
    private final AuthService authService;
    private final LinkTokenService linkTokenService;
    private final UserEntityRepository userEntityRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        //catch 블록에서 어느 흐름(계정 연결 vs 일반 로그인)에서 예외가 발생했는지 구분하기 위해 try 바깥에 선언
        boolean isLinkFlow = false;

        try{
            //인증 결과 확인
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            //인증 플랫폼 확인
            String regId = token.getAuthorizedClientRegistrationId();

            //raw형태의 JSON형태
            Map<String, Object> attributes = (Map<String, Object>) token.getPrincipal().getAttributes();
            log.debug("birthDate={},gender={}",attributes.get("birthDate"),attributes.get("gender"));
            FetchOAuthUserInfo userInfo = switch (regId) {
                case "naver" -> new FetchNaverOAuthUserInfo(attributes);
                case "kakao" -> new FetchKakaoOAuthUserInfo(attributes);
                case "google" -> new FetchGoogleOAuthUserInfo(attributes);
                //필터 체인 중간 오류
                default -> throw new IllegalStateException("Unexpected value: " + regId);
            };

            //계정 연결중인 경우
            String linkCookieName = linkTokenService.getLinkCookieName();
            //링크 쿠키 존재시 파싱
            String linkCookie = readCookie(request,linkCookieName);
            isLinkFlow = (linkCookie != null);

            //link Cookie 존재시 -> 계정 연결 로직만 수행 후 반환
            //안에서 예외도 처리한다. 노션 OAuth 로그인 3-1, 3-2번 참조
            if(linkCookie!=null){
                //쿠키 정보 파싱 (링크 원하는 계정의 이메일)
                String ownerEmail = linkTokenService.parseLinkCookie(linkCookie);
                //현재 사용자의 정보 얻기
                UserEntity user = userEntityRepository.findByUserEmail(ownerEmail).orElseThrow(()->new ApiException(USER_NOT_FOUND));
                //현재 사용자 정보에 OAuth 프로필 정보를 담아 연결, 실패시 메서드에서 예외를 던짐
                socialLoginService.attachUserOAuth(user,userInfo);
                //쿠키 제거
                clearCookie(response,linkCookieName);
                // LinkResponseDto responseDto = LinkResponseDto.builder()
                //         .isLinked(true)
                //         .provider(SocialProvider.valueOf(regId.toUpperCase()))
                //         .build();
                //void 반환형이라 직접 HttpServletResponse에 응답 message body를 담아야 한다
                // writeJsonResponse(response,HttpServletResponse.SC_OK,responseDto);
                //계정 연결 성공 → 프론트 설정 페이지로 리다이렉트, 어떤 provider가 연결됐는지 query param으로 전달
                response.sendRedirect("/settings/account?linked=" + regId);
                return;
            }

            //그렇지 않을 경우 -> 일반 로그인 인증 로직 수행
            //이미 upsert... 메서드에서 신규 회원일 시 UserEntity 생성까지 다 하기 때문에 바로 정보 받아올 수 있음.
            SocialLoginResultDto loginResult = socialLoginService.upsertOAuthInfo(userInfo);
            UserEntity loginUser = loginResult.getExistingUserEntity();

            //헤더 정보 받아오기
            String deviceId = (request.getHeader("X-DEVICE-ID")==null||request.getHeader("X-DEVICE-ID").isBlank())?"unknown":request.getHeader("X-DEVICE-ID");
            String userAgent = (request.getHeader("User-Agent")==null||request.getHeader("User-Agent").isBlank())?"unknown":request.getHeader("User-Agent");

            //인증 절차 진행
            TokenResponseDto tokenResponseDto = authService.loginIssue(loginUser.getUserEmail(), deviceId, userAgent, response);
            // OAuthLoginTokenDto oAuthLoginTokenDto = OAuthLoginTokenDto.builder()
            //         .userEmail(loginUser.getUserEmail())
            //         .nickname(loginUser.getNickname())
            //         .accessToken(tokenResponseDto.getAccessToken())
            //         .accessTokenTtl(tokenResponseDto.getAccessTokenTtl())
            //         .isPasswordSet(loginUser.getPasswordSet())
            //         .isNewUser(loginResult.getIsNewUser())
            //         .build();
            // writeJsonResponse(response,HttpServletResponse.SC_OK,oAuthLoginTokenDto);

            //로그인 성공 → 프론트 콜백 페이지로 리다이렉트
            //accessToken은 URL fragment(#)로 전달 — fragment는 브라우저에서만 처리되며 서버 로그에 남지 않아 query param보다 안전하다.
            //refresh_token은 authService.loginIssue() 내부에서 이미 HttpOnly 쿠키로 심어진다.
            //온보딩 분기에 필요한 isNewUser, isPasswordSet도 함께 전달한다.
            response.sendRedirect(
                "/oauth/callback"
                + "#accessToken=" + tokenResponseDto.getAccessToken()
                + "&isNewUser=" + loginResult.getIsNewUser()
                + "&isPasswordSet=" + loginUser.getPasswordSet()
            );
        } catch (ApiException e){
            //OAuth는 브라우저 리다이렉트 기반이므로 에러도 JSON 대신 프론트로 리다이렉트한다.
            //에러 코드를 query param으로 전달해 프론트에서 에러 종류에 맞는 메시지를 표시할 수 있게 한다.
            log.warn("OAuth failed: errorCode={}, message={}", e.getErrorCode().name(), e.getMessage());
            if(isLinkFlow){
                //계정 연결 중 에러 → 설정 페이지로
                response.sendRedirect("/settings/account?error=" + e.getErrorCode().name());
            } else {
                //일반 로그인 중 에러 → 로그인 페이지로
                response.sendRedirect("/login?error=" + e.getErrorCode().name());
            }
        }
    }

    //링크 토큰 제거
    //같은 설정과 maxAge 0을 통해 제거
    private void clearCookie(HttpServletResponse response, String linkCookieName) {
        ResponseCookie clear = ResponseCookie.from(linkCookieName, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(0)
                .path("/")
                .build();
        response.addHeader("Set-Cookie", clear.toString());
    }

    private String readCookie(HttpServletRequest request, String linkCookieName) {
        Cookie[] cookies = request.getCookies();
        if(cookies == null){
            return null;
        }
        for(Cookie cookie : cookies){
            if(cookie.getName().equals(linkCookieName)){
                return cookie.getValue();
            }
        }
        return null;
    }

    // 리다이렉트 방식 변경으로 더 이상 쓰지 않음
    // private void writeJsonResponse(HttpServletResponse response, int statusCode, Object messageBody) throws IOException {
    //     response.setStatus(statusCode);
    //     response.setContentType("application/json;charset=UTF-8");
    //     objectMapper.writeValue(response.getOutputStream(),messageBody);
    // }
}
