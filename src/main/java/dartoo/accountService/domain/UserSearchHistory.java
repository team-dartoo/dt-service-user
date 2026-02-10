package dartoo.accountService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_search_history",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_search_user_query", columnNames = {"user_id", "query"})
        },
        indexes = {
                @Index(name = "idx_user_search_user_searched", columnList = "user_id, searched_at DESC")
        }
)
public class UserSearchHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="user_id")
    private UserEntity user;

    @Column(nullable = false)
    private String query;

    private Instant searchedAt; //마지막으로 검색한 시각이기 때문에, createdAt을 사용하지 않는다.

    public void search(Instant now) {
        this.searchedAt = now;
    }
}
/*
- INDEX `(user_id, searched_at DESC)` : DB에 저장시 이미 정렬된 상태로 (매번 정렬되면 오래 걸리니까)
- 같은 검색어 또 치면 중복 row 쌓지 말고 최신화 - `(user_id, query, scope)` unique 걸고 `searched_at` 업데이트
- 최근 90일 검색어 or TOP 20~30개 정도만 저장하고 나머지는 삭제
 */