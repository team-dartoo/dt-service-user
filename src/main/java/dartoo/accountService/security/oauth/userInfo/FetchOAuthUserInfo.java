package dartoo.accountService.security.oauth.userInfo;

import dartoo.accountService.domain.SocialProvider;
import dartoo.accountService.dto.FetchProfileDto;

public interface FetchOAuthUserInfo {
    String getEmail();
    String getNickname();
    String getProviderId();
    SocialProvider getProvider();
    FetchProfileDto getProfile();
}
