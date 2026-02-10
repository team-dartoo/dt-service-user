package dartoo.accountService.dto.core;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Builder
@Data
public class BookmarkResponse {
    private String corpId;
    private String corpName;
    private Instant createdAt;
}
