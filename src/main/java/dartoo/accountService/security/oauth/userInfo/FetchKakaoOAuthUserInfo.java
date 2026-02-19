package dartoo.accountService.security.oauth.userInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dartoo.accountService.domain.enums.Gender;
import dartoo.accountService.domain.enums.SocialProvider;
import dartoo.accountService.dto.oauth.FetchProfileDto;

import java.util.Map;

public class FetchKakaoOAuthUserInfo implements FetchOAuthUserInfo{

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final JsonNode root;

    //카카오는 구조가 복잡해서 중첩 JSON 구조를 파일 디렉토리처럼 탐색하게 해주는
    //JsonNode 클래스를 사용하면 깔끔하게 표기할 수 있다.
    public FetchKakaoOAuthUserInfo(Map<String, Object> attributes) {
        this.root = OBJECT_MAPPER.valueToTree(attributes);
    }

    @Override
    public String getEmail() {
        String email = root.at("/kakao_account/email").asText();
        return (email==null||email.isBlank()) ? null : email;
    }

    @Override
    public String getNickname() {
        String nickname = root.at("/kakao_account/profile/nickname").asText();
        // 1) profile 닉네임이 비어있으면 properties 확인
        if (nickname.isBlank()) {
            String properties = root.at("/properties/nickname").asText("");
            if (!properties.isBlank()) {
                return properties;
            }
            return null;
        }

        return nickname;
    }

    @Override
    public String getProviderId() {
        String id = root.path("id").asText(null);
        return (id==null || id.isBlank()) ? null : id;
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public FetchProfileDto getProfile() {
        return FetchProfileDto.builder()
                .email(getEmail())
                .nickname(getNickname())
                .providerId(getProviderId())
                .provider(getProvider())
                .birthday(null)
                .gender(Gender.UNKNOWN)
                .build();
    }
}
