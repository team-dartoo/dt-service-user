package dartoo.accountService.security.oauth.userInfo;

import dartoo.accountService.domain.enums.SocialProvider;
import dartoo.accountService.dto.oauth.FetchProfileDto;

public interface FetchOAuthUserInfo {
    String getEmail();
    String getNickname();
    String getProviderId();
    SocialProvider getProvider();
    FetchProfileDto getProfile();
}
