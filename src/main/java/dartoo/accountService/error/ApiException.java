package dartoo.accountService.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;
    public ApiException(ErrorCode code, String message){ super(message); this.errorCode = code; }
}
