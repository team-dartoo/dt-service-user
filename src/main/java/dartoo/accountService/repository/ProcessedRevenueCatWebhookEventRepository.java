package dartoo.accountService.repository;

import dartoo.accountService.domain.ProcessedRevenueCatWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedRevenueCatWebhookEventRepository extends JpaRepository<ProcessedRevenueCatWebhookEvent,Long> {
}
