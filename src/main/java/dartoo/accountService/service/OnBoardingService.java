package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.dto.oauth.OnBoardingRequestDto;
import dartoo.accountService.dto.oauth.OnBoardingResponseDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static dartoo.accountService.error.ErrorCode.USER_ALREADY_ONBOARDED;
import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional
public class OnBoardingService {
    private final UserEntityRepository userEntityRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    //비밀번호와 프로필 초기화
    public OnBoardingResponseDto initOnBoarding(String email, OnBoardingRequestDto onBoardingRequestDto){
        if(!Objects.equals(email, onBoardingRequestDto.getEmail())){
            throw new AccessDeniedException("사용자 정보가 일치하지 않습니다.");
        }
        UserEntity user = userEntityRepository.findByUserEmail(email)
                .orElseThrow(()-> new ApiException(USER_NOT_FOUND));

        //비밀번호 설정이 이미 되어있으면 온보딩 필요 없음.
        //마이페이지에서 프로필 수정하면 됨
        if(user.getPasswordSet()==Boolean.TRUE){
            throw new ApiException(USER_ALREADY_ONBOARDED);
        }

        String encodedPassword = passwordEncoder.encode(onBoardingRequestDto.getPassword());
        user.changePassword(encodedPassword);

        user.changeProfile(onBoardingRequestDto.getNickname(), onBoardingRequestDto.getBirthday(), onBoardingRequestDto.getGender());

        return OnBoardingResponseDto.builder()
                .userEmail(user.getUserEmail())
                .isPasswordSet(true)
                .nickname(user.getNickname())
                .birthday(user.getBirthday())
                .gender(user.getGender())
                .build();
    }
}
//온보딩 - 정식 유저가 되기 전의 일회성 초기 설정 단계
//비밀번호 초기화와 OAuth에서 불러온 정보가 맞는지 확인 후 세팅
/*
온보딩 API를 따로 만드는 이유 (요약)

1. 온보딩은 비밀번호가 아직 없고, 프로필이 미완성 단계인 경우에만 호출되는 특수한 작업
    -> 아예 다른 상태에서 시행되는 작업
2. 온보딩은 여러개를 한번에 처리해야 함 -> 따로따로 여러 API를 호출하면 분기 로직 등이 번거로움
3. 비밀번호 미설정이라는 특수한 상황시 강제로 이동시키기 위함
4. 1회성 로직이기 때문에 다른 서비스랑 통합되어 운영되면 코드가 복잡해짐
 */