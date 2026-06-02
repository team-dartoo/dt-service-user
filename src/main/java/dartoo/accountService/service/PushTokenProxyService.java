package dartoo.accountService.service;

import com.fasterxml.jackson.databind.JsonNode;
import dartoo.accountService.dto.push.PushTokenResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

import static dartoo.accountService.error.ErrorCode.EXTERNAL_SERVICE_ERROR;
import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushTokenProxyService {

    private final RestClient notificationServiceRestClient;
    private final UserEntityRepository userEntityRepository;

    public PushTokenResponse registerPushToken(String email, String deviceId, String fcmToken) {
        Long userId = userEntityRepository.findByUserEmail(email)
                .orElseThrow(() -> new ApiException(USER_NOT_FOUND))
                .getId();

        Map<String, Object> body = Map.of(
                "user_id", String.valueOf(userId),
                "device_id", deviceId,
                "fcm_token", fcmToken,
                "platform", "web"
        );

        try {
            return notificationServiceRestClient.post()
                    .uri("/api/fcm-tokens")
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("Notification-service returned {} for userId={}", res.getStatusCode(), userId);
                        throw new ApiException(EXTERNAL_SERVICE_ERROR,
                                "알림 서비스 호출 실패: HTTP " + res.getStatusCode().value());
                    })
                    .body(PushTokenResponse.class);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call notification-service for userId={}: {}", userId, e.getMessage());
            throw new ApiException(EXTERNAL_SERVICE_ERROR, "알림 서비스 연동 중 오류가 발생했습니다.");
        }
    }

    public Optional<PushTokenResponse> getPushToken(String email, String deviceId) {
        Long userId = userEntityRepository.findByUserEmail(email)
                .orElseThrow(() -> new ApiException(USER_NOT_FOUND))
                .getId();

        try {
            JsonNode root = notificationServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/fcm-tokens")
                            .queryParam("user_id", String.valueOf(userId))
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("Notification-service returned {} while fetching token for userId={}", res.getStatusCode(), userId);
                        throw new ApiException(EXTERNAL_SERVICE_ERROR,
                                "알림 서비스 호출 실패: HTTP " + res.getStatusCode().value());
                    })
                    .body(JsonNode.class);

            if (root == null || !root.has("tokens")) {
                return Optional.empty();
            }

            for (JsonNode token : root.get("tokens")) {
                if (deviceId.equals(token.path("deviceId").asText(""))) {
                    return Optional.of(PushTokenResponse.builder()
                            .userId(Long.valueOf(token.path("userId").asText(String.valueOf(userId))))
                            .deviceId(token.path("deviceId").asText())
                            .fcmToken(token.path("fcmToken").asText())
                            .build());
                }
            }
            return Optional.empty();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch notification-service tokens for userId={}: {}", userId, e.getMessage());
            throw new ApiException(EXTERNAL_SERVICE_ERROR, "알림 서비스 연동 중 오류가 발생했습니다.");
        }
    }

    public void deactivatePushToken(String email, String deviceId) {
        Long userId = userEntityRepository.findByUserEmail(email)
                .orElseThrow(() -> new ApiException(USER_NOT_FOUND))
                .getId();

        try {
            notificationServiceRestClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/fcm-tokens/{deviceId}")
                            .queryParam("user_id", String.valueOf(userId))
                            .build(deviceId))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("Notification-service returned {} while deactivating token for userId={}", res.getStatusCode(), userId);
                        throw new ApiException(EXTERNAL_SERVICE_ERROR,
                                "알림 서비스 호출 실패: HTTP " + res.getStatusCode().value());
                    })
                    .toBodilessEntity();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to deactivate notification-service token for userId={}: {}", userId, e.getMessage());
            throw new ApiException(EXTERNAL_SERVICE_ERROR, "알림 서비스 연동 중 오류가 발생했습니다.");
        }
    }
}
