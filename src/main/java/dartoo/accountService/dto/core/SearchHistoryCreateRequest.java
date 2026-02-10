package dartoo.accountService.dto.core;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SearchHistoryCreateRequest {
    @NotBlank(message = "검색어를 입력해주세요.")
    private String query;
}
