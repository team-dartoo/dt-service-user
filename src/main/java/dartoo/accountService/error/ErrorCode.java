package dartoo.accountService.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum ErrorCode {
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST,  "현재 비밀번호가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
