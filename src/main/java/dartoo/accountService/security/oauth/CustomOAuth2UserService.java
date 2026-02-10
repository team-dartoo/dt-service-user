package dartoo.accountService.security.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * CustomOAuth2UserService
 *  - 스프링부트가 알아서 application.yml에 있는 authorization-uri, token-uri, user-info-uri를 보고
 *  인증 코드와 API 이용 토큰을 이용해 사용자 프로필을 받아온 뒤, 어떻게 처리해서 백엔드에 넘겨줄 지 결정하는 클래스
 *
 * - 기본(DefaultOAuth2UserService)은 각 provider의 /userinfo 응답(JSON)을 그대로 attributes에 담아준다.
 * - 구글/카카오는 응답이 평탄한 형태라 바로 써도 되지만,
 *   네이버는 아래와 같이 한 번 더 래핑된 형태로 응답한다.
 *
 *   {
 *     "resultcode": "00",
 *     "message": "success",
 *     "response": {
 *        "id": "...",
 *        "email": "...",
 *        "name": "..."
 *     }
 *   }
 *
 * - 우리가 실제로 쓰고 싶은 정보는 모두 "response" 안에 있기 때문에,
 *   네이버일 때만 attributes를 response 맵으로 교체해서
 *   { "id": "...", "email": "...", "name": "..." } 이런 형태로 평탄화해서 반환한다.
 *
 * - 이렇게 해 두면 나중에 OAuthLoginSuccessHandler나 OAuth2UserInfo 구현체에서
 *   구글/카카오/네이버를 모두 비슷한 방식으로 파싱할 수 있고,
 *   providerUserId는 항상 attrs.get("id") / 구글은 attrs.get("sub") 등으로 일관되게 다룰 수 있다.
 */
@RequiredArgsConstructor
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final RestClient restClient = RestClient.create();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);
        String regId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = new HashMap<>(user.getAttributes());

        // 구글/카카오는 그대로 사용 가능
        // 구글은 Google People API 사용해서 추가적인 개인정보 호출
        if (regId.equals("google")){
            String accessToken = userRequest.getAccessToken().getTokenValue();
            Map<String, Object> extraInfo = fetchGooglePeopleProfile(accessToken);
            attributes.putAll(extraInfo);
            return new DefaultOAuth2User(user.getAuthorities(), attributes, "sub");
        }
        else if (regId.equals("naver")) {
            Object response = attributes.get("response");
            //자동으로 Map으로 캐스팅한 뒤 비교
            if (response instanceof Map<?, ?> responseMap) {
                return new DefaultOAuth2User(user.getAuthorities()
                        , (Map<String, Object>) responseMap, "id");
            }
            return user;
        }
        else{
            return user;
        }
    }

    /*
        Google People API 호출해서 정보 가져오는 메서드
        현재는 birthdate와 gender만 가져온다.
     */
    private Map<String, Object> fetchGooglePeopleProfile(String accessToken) {
        //API 수동 호출
        JsonNode root = restClient
                .get()
                .uri("https://people.googleapis.com/v1/people/me?personFields=birthdays,genders")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

        /*
        uriBuilder을 사용하는 방법도 있음

                .uri(uriBuilder -> uriBuilder
                .scheme("https")
                .host("people.googleapis.com")
                .path("/v1/people/me")
                .queryParam("personFields", "birthdays,genders")
                .build())
        일단 여기 한번만 사용하는 것이라 참고용 주석으로만 기재
         */

        //정보 불러오기 실패하면 빈 노드로
        if(root == null || root.isMissingNode()) return Map.of();

        Map<String,Object> result = new HashMap<>();

        //생일 파싱
        LocalDate birthday = null;
        for (JsonNode b : root.path("birthdays")) {
            boolean primary = b.path("metadata").path("primary").asBoolean(false);
            JsonNode date = b.path("date");
            int year = date.path("year").asInt(0);
            int month = date.path("month").asInt(0);
            int day = date.path("day").asInt(0);

            if (year > 0 && month > 0 && day > 0) {
                birthday = LocalDate.of(year, month, day);
                if (primary) break; // primary 있으면 바로 사용
            }
        }
        if (birthday != null) {
            result.put("birthDate", birthday.toString()); // "YYYY-MM-DD"
        }
        //성별 파싱
        String gender = null;
        for (JsonNode g : root.path("genders")) {
            boolean primary = g.path("metadata").path("primary").asBoolean(false);
            String value = g.path("value").asText(null); // "male", "female", etc.
            if (value != null) {
                gender = value;
                if (primary) break;
            }
        }
        if (gender != null) {
            result.put("gender", gender); // 그대로 문자열로 저장
        }
        return result;
    }
}
/*
참고
Google 표준 userinfo 응답 JSON 구조 예시
{
  "sub": "110169484474386276334",// 구글 계정 고유 ID (변하지 않음)
  "name": "홍길동",
  "given_name": "길동",
  "family_name": "홍",
  "picture": "https://lh3.googleusercontent.com/...",
  "email": "user@gmail.com",
  "email_verified": true,
  "locale": "ko"
}

구글 OAuth의 profile scope에서는
name, picture (프로필 이미지 URL), locale, email_verified를 제공하고

성별, 생일, 생년월일, 나이대, 휴대폰 번호 등은 제공하지 않기 때문에,
Google People API라는 별도의 API를 사용해 로드해야 한다.

https://www.googleapis.com/auth/user.birthday.read 등등을
application.yml의 scope에 넣고, 프로필을 만들 때 수동으로 호출해줘야 한다.

Kakao 표준 userinfo 응답 JSON 구조 예시 (동의 사항에 따라 세부 구조는 다름)
{
  "id": 1234567890,                       // 카카오 내부 유저 ID (고정, 변하지 않음)
  "connected_at": "2021-01-01T00:00:00Z",
  "kakao_account": {
    "profile_needs_agreement": false,
    "profile": {
      "nickname": "홍길동",
      "thumbnail_image_url": "http://...",
      "profile_image_url": "http://..."
    },
    "email_needs_agreement": false,
    "is_email_valid": true,
    "is_email_verified": true,
    "email": "user@kakao.com"
  }
}
*/