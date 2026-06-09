package dartoo.accountService.dto.core;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BookmarkReorderRequest {
    @NotEmpty(message = "정렬 순서 목록이 필요합니다.")
    private List<String> corpCodes;
}
