package dartoo.accountService.web;

import dartoo.accountService.dto.oauth.OnBoardingRequestDto;
import dartoo.accountService.dto.oauth.OnBoardingResponseDto;
import dartoo.accountService.service.OnBoardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//온보딩 - 정식 유저가 되기 전의 일회성 초기 설정 단계
@RestController
@RequestMapping("/api/users/onboarding")
@RequiredArgsConstructor
public class OnBoardingController {
    private final OnBoardingService onBoardingService;

    /*
     * - 비밀번호가 아직 없는 계정에 한해 비밀번호를 최초로 설정하고,
     *   프로필 정보(닉네임/생년월일/성별)를 한 번에 갱신한다.
     * - 비밀번호를 새로 설정한 경우, 기존 RefreshToken 들은 모두 revoke 된다.
     *
     *  - 최초 로그인 성공 후, 백엔드에서 "온보딩 필요" 플래그를 내려줌 (isPasswordSet=false)
     *  - 클라이언트는 온보딩 페이지로 이동 후, 사용자의 프로필 설정이 끝났으면 이 API 를 호출
     */
    @PostMapping("/complete")
    public ResponseEntity<OnBoardingResponseDto> initOnBoarding(@Valid @RequestBody OnBoardingRequestDto onBoardingRequestDto,
                                               Authentication auth){
        String email = auth.getName();
        OnBoardingResponseDto responseDto = onBoardingService.initOnBoarding(email, onBoardingRequestDto);
        return ResponseEntity.ok(responseDto);
    }

    //정보 조회는 기존의 GET /api/users/info 사용하면 됨
}
