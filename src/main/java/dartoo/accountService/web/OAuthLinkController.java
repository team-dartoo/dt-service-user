package dartoo.accountService.web;

import dartoo.accountService.security.oauth.LinkTokenService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

//마이페이지에서 OAuth 계정을 기존 계정에 연결하고 싶을 때 사용하는 컨트롤러
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/users/auth/link")
public class OAuthLinkController {
    private final LinkTokenService linkTokenService;

    @PostMapping("/{provider}")
    public ResponseEntity<Void> linkAccount(@PathVariable String provider, Authentication auth,
                                            HttpServletResponse response) throws IOException {
        String email = auth.getName();
        //누가 계정 연결을 하고 싶어하는지 쿠키 정보에 담음
        linkTokenService.issueLinkCookie(email, response);
        //이후 로그인으로 리다이렉트 -> 쿠키유무 보고 계정 연결 여부 결정
        //"Location"에 적혀있는 URL: 스프링 시큐리티가 OAuth 로그인을 위해 자동으로 만들어주는 URL
        response.setHeader("Location", "/oauth2/authorization/"+provider);
        return ResponseEntity.status(HttpStatus.FOUND).build();
    }
}
