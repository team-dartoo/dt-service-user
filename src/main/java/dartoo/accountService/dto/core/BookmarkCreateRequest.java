package dartoo.accountService.dto.core;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BookmarkCreateRequest {
    @NotBlank(message = "회사코드를 입력해주세요.")
    private String corpCode;

    @NotBlank(message = "회사명을 입력해주세요.")
    private String corpName;
}
