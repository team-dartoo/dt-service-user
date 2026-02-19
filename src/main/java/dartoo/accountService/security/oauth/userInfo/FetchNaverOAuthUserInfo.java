package dartoo.accountService.security.oauth.userInfo;

import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.SocialProvider;
import dartoo.accountService.dto.oauth.FetchProfileDto;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@AllArgsConstructor
public class FetchNaverOAuthUserInfo implements FetchOAuthUserInfo{

    private final Map<String, Object> attributes;

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getNickname() {
        return (String) attributes.get("name");
    }

    @Override
    public String getProviderId() {
        return (String) attributes.get("id");
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.NAVER;
    }

    /*
    네이버는 응답형에 birthday("MM-DD")와 birthyear("YYYY")가 분리되어 있어, 통합시켜 반환할 예정
     */
    public LocalDate getBirthday() {
        Object birthdate = attributes.get("birthday");
        Object birthyear = attributes.get("birthyear");

        if (birthdate == null || birthyear == null) {
            return null; // 정보 없으면 null
        }

        try {
            String birthStr = birthdate.toString();
            String yearStr  = birthyear.toString();

            String[] parts = birthStr.split("-");
            if (parts.length != 2) return null;

            int month = Integer.parseInt(parts[0]);
            int day   = Integer.parseInt(parts[1]);
            int year     = Integer.parseInt(yearStr);

            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null; // 포맷 이상하면 null로 반환
        }
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
                .birthday(null)
                .gender(Gender.UNKNOWN)
                .build();
    }
}
