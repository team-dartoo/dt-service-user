package dartoo.accountService.service.revenuecat;

import dartoo.accountService.error.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static dartoo.accountService.error.ErrorCode.REFUND_FAILED;

@Component
@Slf4j
public class RevenueCatRefundClient {

    @Value("${revenuecat.api.key}")
    private String apiKey;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://api.revenuecat.com")
            .build();

    //환불 API 호출하기
    public void refund(String appUserId, String transactionId){
        log.info("[Refund] 환불 요청: appUserId={}, transactionId={}", appUserId, transactionId);
        try {
            restClient.post()
                    .uri("/v1/subscribers/{appUserId}/transactions/{transactionId}/refund",appUserId,transactionId)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .retrieve() // HTTP 요청 실행
                    .toBodilessEntity(); //응답 바디가 필요 없을 시 사용
            log.info("[Refund] 환불 완료: appUserId={}, transactionId={}", appUserId, transactionId);
        } catch (RestClientException e){
            log.error("[Refund] 환불 API 호출 실패: appUserId={}, transactionId={}", appUserId, transactionId, e);
            throw new ApiException(REFUND_FAILED);
        }
    }
}
