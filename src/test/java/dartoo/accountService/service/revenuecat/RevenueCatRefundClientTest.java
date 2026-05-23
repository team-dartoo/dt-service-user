package dartoo.accountService.service.revenuecat;

import dartoo.accountService.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static dartoo.accountService.error.ErrorCode.REFUND_FAILED;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RevenueCatRefundClientTest {

    @InjectMocks
    private RevenueCatRefundClient revenueCatRefundClient;

    // RestClient는 내부에서 직접 mock을 생성
    // → RestClient를 mock으로 교체하는 방식 사용
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        //@Value로 주입하기
        ReflectionTestUtils.setField(revenueCatRefundClient, "apiKey", "test-api-key");

        // RestClient는 RevenueCatRefundClient 내부에서 직접 생성(new)하는 필드라
        // @Mock으로 주입할 수 없다. 따라서 mock 개체를 직접 주입해야 한다.
        RestClient mockRestClient = mock(RestClient.class);

        // RestClient의 메서드 체이닝 구조를 mock으로 구성한다.
        // restClient.post()
        //   .uri(...)
        //   .header(...)
        //   .header(...)
        //   .retrieve()
        //   .toBodilessEntity()
        // 각 단계가 다음 단계의 객체를 반환하므로 단계별로 mock을 만들어야 한다.
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        ReflectionTestUtils.setField(revenueCatRefundClient, "restClient", mockRestClient);

        // 각 체이닝 단계가 다음 mock 객체를 반환하도록 연결한다.
        given(mockRestClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString(), anyString(), anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.header(eq("Authorization"), anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.header(eq("Content-Type"), anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
        // toBodilessEntity()는 각 테스트에서 성공/실패 케이스별로 다르게 설정한다.
    }

    @Test
    @DisplayName("환불 API 호출 성공")
    void refund_success() {
        //given
        given(responseSpec.toBodilessEntity()).willReturn(null);

        //when & then
        assertThatCode(() -> revenueCatRefundClient.refund("user@test.com", "tx_001"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("환불 API 호출 실패 → REFUND_FAILED 예외")
    void refund_fail_throwsRefundFailed() {
        //given
        given(responseSpec.toBodilessEntity())
                .willThrow(new RestClientException("connection error"));

        //when & then
        assertThatThrownBy(() -> revenueCatRefundClient.refund("user@test.com", "tx_001"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", REFUND_FAILED);
    }
}