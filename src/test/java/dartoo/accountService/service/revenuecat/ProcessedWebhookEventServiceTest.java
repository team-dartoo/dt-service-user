package dartoo.accountService.service.revenuecat;

import dartoo.accountService.domain.ProcessedRevenueCatWebhookEvent;
import dartoo.accountService.dto.webhook.RevenueCatWebhookPayload;
import dartoo.accountService.repository.ProcessedRevenueCatWebhookEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessedWebhookEventServiceTest {

    @Mock
    private ProcessedRevenueCatWebhookEventRepository processedRevenueCatWebhookEventRepository;

    @InjectMocks
    private ProcessedWebhookEventService processedWebhookEventService;

    @Test
    @DisplayName("최초 수신 이벤트 → 저장 성공, true 반환")
    void markAsFirstDelivery_firstTime_returnsTrue() {
        //given
        RevenueCatWebhookPayload.Event event = buildEvent("event-001", "INITIAL_PURCHASE", "user@test.com");
        given(processedRevenueCatWebhookEventRepository.saveAndFlush(any()))
                .willReturn(any());

        //when
        boolean result = processedWebhookEventService.markAsFirstDelivery(event);

        //then
        // 상태 검증
        assertThat(result).isTrue();
        // 상호작용 검증
        then(processedRevenueCatWebhookEventRepository).should().saveAndFlush(any());
    }

    @Test
    @DisplayName("중복 이벤트 수신 → unique 충돌, false 반환")
    void markAsFirstDelivery_duplicate_returnsFalse() {
        //given
        RevenueCatWebhookPayload.Event event = buildEvent("event-001", "INITIAL_PURCHASE", "user@test.com");
        given(processedRevenueCatWebhookEventRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("unique constraint violation"));

        //when
        boolean result = processedWebhookEventService.markAsFirstDelivery(event);

        //then
        // 상태 검증: 중복이므로 false
        assertThat(result).isFalse();
    }

    private RevenueCatWebhookPayload.Event buildEvent(String id, String type, String appUserId) {
        RevenueCatWebhookPayload.Event event = new RevenueCatWebhookPayload.Event();
        ReflectionTestUtils.setField(event, "id", id);
        ReflectionTestUtils.setField(event, "type", type);
        ReflectionTestUtils.setField(event, "app_user_id", appUserId);
        return event;
    }
}