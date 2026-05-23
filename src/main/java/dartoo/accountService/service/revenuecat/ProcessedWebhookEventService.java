package dartoo.accountService.service.revenuecat;

import dartoo.accountService.domain.ProcessedRevenueCatWebhookEvent;
import dartoo.accountService.dto.webhook.RevenueCatWebhookPayload;
import dartoo.accountService.repository.ProcessedRevenueCatWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessedWebhookEventService {

    private final ProcessedRevenueCatWebhookEventRepository processedRevenueCatWebhookEventRepository;

    /**
     * 동일 webhook의 중복 처리를 방지한다.
     *
     * RevenueCat webhook은 at-least-once 방식으로 전달될 수 있으므로,
     * 같은 event.id가 재전송될 가능성을 고려해야 한다.
     *
     * 처리 방식:
     *  1. event.id를 unique 제약이 걸린 테이블에 저장 시도
     *  2. 저장 성공  -> 최초 수신 이벤트이므로 true 반환
     *  3. unique 충돌 -> 이미 처리한 동일 이벤트이므로 false 반환
     *
     * 주의:
     *  - 중복 webhook은 오류가 아니라 정상 무시 케이스다.
     *  - 따라서 false를 반환하고 handleWebhook()에서 조용히 return 하도록 한다.
     *  - event.id는 webhook 이벤트 자체의 고유 ID이며,
     *    transaction_id와는 다르다. (transaction_id는 결제 건 식별용)
     *
     * REQUIRES_NEW:
     *  - 중복 체크 저장만 별도 트랜잭션으로 처리한다.
     *  - 같은 event.id가 다시 들어와 저장이 실패해도,
     *    현재 webhook 전체 처리까지 실패하지 않도록 별도 트랜잭션으로 분리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markAsFirstDelivery(RevenueCatWebhookPayload.Event event) {
        try{
            // unique(event_id) 제약이 걸린 테이블에 즉시 반영한다.
            // saveAndFlush()를 사용해 트랜잭션 종료 시점까지 미루지 않고
            // 지금 이 시점에 중복 여부를 바로 확인한다.
            processedRevenueCatWebhookEventRepository.saveAndFlush(
                    ProcessedRevenueCatWebhookEvent.builder()
                            .eventId(event.getId())
                            .eventType(event.getType())
                            .appUserId(event.getApp_user_id())
                            .receivedAt(Instant.now()).build()
            );
            return true;
            // DB에 쓰기 실패 시, 예외 호출
        } catch (DataIntegrityViolationException e){
            // 동일 event.id가 이미 저장되어 있다면
            // 같은 webhook이 재전송된 것으로 보고 중복 처리 없이 종료한다.
            log.info("[Webhook] 중복 이벤트 무시: eventId={}, type={}, app_user_id={}",
                    event.getId(), event.getType(), event.getApp_user_id());
            return false;
        }
    }
}
/*
[REQUIRES_NEW를 사용하는 이유]

RevenueCatWebhookService.java의 handleWebhook()은
Secret 검증, 지원 이벤트 확인, 사용자 조회, 플랜 업데이트까지
전체 webhook 처리 흐름을 하나의 큰 트랜잭션으로 수행한다.

그런데 중복 체크를 위해 event.id를 저장하는 과정에서,
이미 저장된 동일 event.id가 또 다시 순간적으로 들어오면
DB unique 제약으로 인해 예외가 발생한다.

우리 의도는 이 예외를 "중복 webhook이므로 정상 무시"로 해석하는 것이다.
즉, catch 후 false를 반환하고 handleWebhook()에서 조용히 return 하면 된다.

하지만 이 저장 작업이 handleWebhook()과 같은 트랜잭션 안에서 수행되면,
겉으로 예외를 catch 했더라도 스프링/DB 입장에서는
"이 트랜잭션 안에서 DB 예외가 한 번 발생했다"는 사실이 남을 수 있다.

이 경우 바깥 트랜잭션이 안쪽에서 일어난 쓰기 실패로 인해,
rollback-only 상태로 표시되거나,
나중에 커밋 시점에서 예상하지 못한 예외가 발생할 수 있다.

그래서 event.id 저장만 REQUIRES_NEW로 분리한다.

즉:
- 중복 체크 저장은 별도 작은 트랜잭션
- 실제 handleWebhook() 처리는 바깥 큰 트랜잭션

이렇게 나누면 중복 event.id 저장 실패가 발생해도
작은 트랜잭션 안에서만 처리되고,
바깥 webhook 처리 흐름 전체는 안전하게 유지할 수 있다.
*/