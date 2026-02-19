package dartoo.accountService.security.oauth;

import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserOAuth;
import dartoo.accountService.dto.oauth.FetchProfileDto;
import dartoo.accountService.dto.oauth.SocialLoginResultDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.UserOAuthRepository;
import dartoo.accountService.security.oauth.userInfo.FetchOAuthUserInfo;
import dartoo.accountService.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static dartoo.accountService.error.ErrorCode.INVALID_PROVIDER_ID;
import static dartoo.accountService.error.ErrorCode.OAuth_ACCOUNT_ALREADY_LINKED;

//OAuth 인증 서버에서 불러온 프로필 정보 기반으로 우리 DB에 있는 사용자 조회 + 업데이트 or 인서트
//컨트롤러/핸들러에서 어떻게 조치를 취해야하는지 알려준다.
@Service
@RequiredArgsConstructor
public class SocialLoginService {
    private final UserEntityRepository userEntityRepository;
    private final UserOAuthRepository userOAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;

    //OAuth 로그인 정보 존재 여부에 따라 회원가입인지 아닌지 여부 결정해서 알려줌.
    @Transactional
    public SocialLoginResultDto upsertOAuthInfo(FetchOAuthUserInfo userInfo){
        if(userInfo.getProviderId() == null){
            throw new ApiException(INVALID_PROVIDER_ID);
        }

        String newEmail = normalize(userInfo.getEmail());

        //해당 OAuth 정보가 있는지 조회
        Optional<UserOAuth> oAuthInfo = userOAuthRepository.
                findByProviderAndProviderUserId(userInfo.getProvider(), userInfo.getProviderId());

        //이미 존재하는 경우 -> 기존 사용자의 우리 웹사이트 쪽 정보 불러옴
        //노션 OAuth 로그인 방법 페이지 2-a
        if(oAuthInfo.isPresent()){
            UserOAuth userOAuth = oAuthInfo.get();

            //네이버처럼 이메일 제공 정보가 바뀔 수 있는 경우 업데이트
            if(newEmail!=null&&!newEmail.equals(userOAuth.getProviderEmail())){
                userOAuth.updateProviderEmail(newEmail);
            }
            return new SocialLoginResultDto(userOAuth.getUserEntity(),false);
        }

        //다른 플랫폼을 통해 로그인했는데, 이미 우리 DB에 있는 이메일로 로그인 한 경우
        //강제 연결 해야함 (노션 OAuth 로그인 페이지 1-a)
        if(newEmail!=null){
            Optional<UserEntity> infoByEmail = userEntityRepository.findByUserEmail(newEmail);
            if(infoByEmail.isPresent()){
                UserEntity userEntity = infoByEmail.get();
                attachUserOAuth(userEntity,userInfo);
//                UserOAuth userOAuth = UserOAuth.builder()
//                        .userEntity(userEntity)
//                        .provider(userInfo.getProvider())
//                        .providerUserId(userInfo.getProviderId())
//                        .providerEmail(newEmail)
//                        .build();
//                userOAuthRepository.save(userOAuth);
                return new SocialLoginResultDto(userEntity,false);
            }
        }

        //우리 DB에 없는 이메일 정보인 경우
        // -> 신규 가입 유저임을 알리고, 기본 프로필 정보로 OAuth 인증 서버에서 제공한 사용자 프로필 정보 이용
        // 노션 OAuth 로그인 페이지 1-a
        UserEntity newUser = createNewUser(userInfo,newEmail);
        UserEntity savedUser = userService.saveUserWithDefaultSettings(newUser);
        attachUserOAuth(savedUser,userInfo);
        return new SocialLoginResultDto(newUser,true);
    }

    //리팩토링한 UserEntity 빌더
    private UserEntity createNewUser(FetchOAuthUserInfo userInfo, String newEmail) {
        //fetch 했을 때의 데이터가 null일수도 있기 때문에 기본값올 채워넣는 로직 추가
        //프로필 fetch 하는 함수에서 해도 되지만, 각 클래스의 역할 분담을 위해 여기서 진행
        //가끔씩 이메일을 인증 서버에서 안보내주는 경우가 있음. (사용자가 비동의, 기업계정인 경우)
        if(newEmail == null || newEmail.isBlank()){
            newEmail = userInfo.getProvider()+"_"+userInfo.getProviderId().substring(0,8)+"@oauth.local";
        }
        String nickname = (userInfo.getNickname()!=null&&!userInfo.getNickname().isBlank()) ?
                userInfo.getNickname():userInfo.getProvider()+"_user_"+userInfo.getProviderId().substring(0,8);

        FetchProfileDto profile = userInfo.getProfile();
        return UserEntity.builder()
                .userEmail(newEmail)
                .password(passwordEncoder.encode("SOCIAL"+ UUID.randomUUID()))
                .passwordSet(false)
                .nickname(nickname)
                .role(Role.USER)
                .gender(profile.getGender())
                .birthday(profile.getBirthday())
                .build();
    }

    //UserOAuth 테이블에 새로운 UserEntity 정보를 연결해서 저장
    //저장 시 이미 정보가 있는 경우에는 예외를 발생시켜서 종료
    //(이에 관련해선 노션 OAuth 로그인 3-b 케이스 참조)
    public void attachUserOAuth(UserEntity owner, FetchOAuthUserInfo userInfo) {
        //이미 연결된 oAuth 정보는 아닌지 확인
        Optional<UserOAuth> oAuthInfo =
                userOAuthRepository.findByProviderAndProviderUserId(userInfo.getProvider(), userInfo.getProviderId());
        //이미 존재하는 정보라면
        if(oAuthInfo.isPresent()){
            UserEntity existUser = oAuthInfo.get().getUserEntity();
            //이미 존재하는 정보인데, 다른 계정이 만들어져 있다면 연결이 불가능하므로 예외처리
            //OAuth 로그인 3-b 케이스
            if(!existUser.getId().equals(owner.getId())){
                throw new ApiException(OAuth_ACCOUNT_ALREADY_LINKED);
            }
            return;
        }

        //그 외의 케이스 -> 새로운 OAuth 정보를 UserEntity에 연결
        UserOAuth newOAuth = UserOAuth.builder()
                .userEntity(owner)
                .provider(userInfo.getProvider())
                .providerUserId(userInfo.getProviderId())
                .providerEmail(normalize(userInfo.getEmail()))
                .build();
        userOAuthRepository.save(newOAuth);
    }

    //이메일 형식 통일하는 유틸 메서드
    private String normalize(String email) {
        if (email == null) return null;
        String v = email.trim();
        if (v.isEmpty()) return null;
        return v.toLowerCase();
    }
}
