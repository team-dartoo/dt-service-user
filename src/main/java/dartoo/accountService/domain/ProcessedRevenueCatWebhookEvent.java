package dartoo.accountService.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "processed_revenuecat_webhook_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processed_revenuecat_event_id",
                columnNames = "event_id"
        )
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedRevenueCatWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "app_user_id", nullable = false, updatable = false)
    private String appUserId;

    @Column(name = "transaction_id", updatable = false)
    private String transactionId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

}
