package dartoo.accountService.security.oauth;

import dartoo.accountService.domain.Role;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserOAuth;
import dartoo.accountService.dto.oauth.SocialLoginResultDto;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.UserOAuthRepository;
import dartoo.accountService.security.oauth.userInfo.FetchOAuthUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static dartoo.accountService.error.ErrorCode.INVALID_PROVIDEER_ID;

//OAuth 인증 서버에서 불러온 프로필 정보 기반으로 우리 DB에 있는 사용자 조회 + 업데이트 or 인서트
//컨트롤러/핸들러에서 어떻게 조치를 취해야하는지 알려준다.
@Service
@RequiredArgsConstructor
public class SocialLoginService {
    private final UserEntityRepository userEntityRepository;
    private final UserOAuthRepository userOAuthRepository;
    private final PasswordEncoder passwordEncoder;

    //OAuth 로그인 정보 존재 여부에 따라 회원가입인지 아닌지 여부 결정해서 알려줌.
    @Transactional
    public SocialLoginResultDto upsertOAuthInfo(FetchOAuthUserInfo userInfo){
        if(userInfo.getProviderId() == null){
            throw new ApiException(INVALID_PROVIDEER_ID);
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
                UserOAuth userOAuth = UserOAuth.builder()
                        .userEntity(userEntity)
                        .provider(userInfo.getProvider())
                        .providerUserId(userInfo.getProviderId())
                        .providerEmail(newEmail)
                        .build();
                userOAuthRepository.save(userOAuth);
                return new SocialLoginResultDto(userEntity,true);
            }
        }
        //우리 DB에 없는 이메일 정보인 경우
        // -> 신규 가입 유저임을 알리고, 기본 프로필 정보로 OAuth 인증 서버에서 제공한 사용자 프로필 정보 이용
        // 노션 OAuth 로그인 페이지 1-a
        UserEntity newUser = createNewUser(userInfo,newEmail);
        attachUserOAuth(newUser,userInfo);
        return new SocialLoginResultDto(newUser,false);
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
        UserEntity newUser =  UserEntity.builder()
                .userEmail(newEmail)
                .password(passwordEncoder.encode("SOCIAL"+ UUID.randomUUID()))
                .passwordSet(false)
                .nickname(nickname)
                .role(Role.USER)
                .build();
        return userEntityRepository.save(newUser);
    }

    //UserOAuth 테이블에 UserEntity 정보를 연결해서 저장
    private void attachUserOAuth(UserEntity newUser, FetchOAuthUserInfo userInfo) {
        UserOAuth newOAuth = UserOAuth.builder()
                .userEntity(newUser)
                .provider(userInfo.getProvider())
                .providerUserId(userInfo.getProviderId())
                .providerEmail(userInfo.getEmail())
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
