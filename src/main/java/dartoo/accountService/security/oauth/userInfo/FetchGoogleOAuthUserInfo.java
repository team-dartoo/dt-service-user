package dartoo.accountService.security.oauth.userInfo;

import dartoo.accountService.domain.Gender;
import dartoo.accountService.domain.SocialProvider;
import dartoo.accountService.dto.FetchProfileDto;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@AllArgsConstructor
public class FetchGoogleOAuthUserInfo implements FetchOAuthUserInfo{
    private final Map<String, Object> attributes;
    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getNickname() {
        return attributes.get("name") == null ? null : (String) attributes.get("name");
    }

    @Override
    public String getProviderId() {
        return (String) attributes.get("sub");
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.GOOGLE;
    }

    public LocalDate getBirthday() {
        Object v = attributes.get("birthDate");
        if (v instanceof String s && !s.isBlank()) {
            return LocalDate.parse(s); // "YYYY-MM-DD" 포맷 기준
        }
        return null;
    }

    public Gender getGender() {
        return attributes.get("gender")==null ? Gender.UNKNOWN : Gender.fromString(attributes.get("gender").toString());
    }

    @Override
    public FetchProfileDto getProfile() {
        return FetchProfileDto.builder()
                .email(getEmail())
                .nickname(getNickname())
                .birthday(getBirthday())
                .gender(getGender())
                .providerId(getProviderId())
                .provider(getProvider())
                .build();
    }
}
