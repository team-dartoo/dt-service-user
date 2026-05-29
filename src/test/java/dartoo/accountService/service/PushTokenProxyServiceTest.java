package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.dto.push.PushTokenResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.EXTERNAL_SERVICE_ERROR;
import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PushTokenProxyServiceTest {

    @Mock RestClient restClient;
    @Mock UserEntityRepository userEntityRepository;
    @Mock RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock RestClient.RequestBodySpec requestBodySpec;
    @Mock RestClient.ResponseSpec responseSpec;

    PushTokenProxyService pushTokenProxyService;

    @BeforeEach
    void setUp() {
        pushTokenProxyService = new PushTokenProxyService(restClient, userEntityRepository);
    }

    private void stubUserFound() {
        UserEntity user = UserEntity.builder()
                .id(1L).userEmail("test@example.com").password("x").nickname("nick").build();
        given(userEntityRepository.findByUserEmail("test@example.com")).willReturn(Optional.of(user));
    }

    private void stubRestClientSuccess(PushTokenResponse resp) {
        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri("/api/fcm-tokens")).willReturn(requestBodySpec);
        given(requestBodySpec.body(any(Object.class))).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.onStatus(any(), any())).willReturn(responseSpec);
        given(responseSpec.body(PushTokenResponse.class)).willReturn(resp);
    }

    @Test
    @DisplayName("정상 등록 → PushTokenResponse 반환")
    void registerPushTokenSuccess() {
        stubUserFound();
        PushTokenResponse expected = PushTokenResponse.builder()
                .userId(1L).deviceId("dev1").fcmToken("tok1").build();
        stubRestClientSuccess(expected);

        PushTokenResponse result = pushTokenProxyService.registerPushToken(
                "test@example.com", "dev1", "tok1");

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getDeviceId()).isEqualTo("dev1");
        assertThat(result.getFcmToken()).isEqualTo("tok1");
    }

    @Test
    @DisplayName("사용자 없음 → USER_NOT_FOUND ApiException")
    void registerPushTokenUserNotFound() {
        given(userEntityRepository.findByUserEmail("missing@example.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> pushTokenProxyService.registerPushToken(
                "missing@example.com", "dev1", "tok1"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(USER_NOT_FOUND);
    }

    @Test
    @DisplayName("RestClient 예외 → EXTERNAL_SERVICE_ERROR ApiException")
    void registerPushTokenRestClientError() {
        stubUserFound();
        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri("/api/fcm-tokens")).willReturn(requestBodySpec);
        given(requestBodySpec.body(any(Object.class))).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> pushTokenProxyService.registerPushToken(
                "test@example.com", "dev1", "tok1"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(EXTERNAL_SERVICE_ERROR);
    }
}
