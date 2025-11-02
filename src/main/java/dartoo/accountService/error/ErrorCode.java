package dartoo.accountService.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum ErrorCode {
    //인증 관련
    INVALID_DEVICE_ID(HttpStatus.UNAUTHORIZED, "유효하지 않은 기기 ID입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED,"유효하지 않은 리프레시 토큰입니다."),
    REFRESH_TOKEN_ALREADY_ROTATED(HttpStatus.UNAUTHORIZED, "이미 회전된 리프레시 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 리프레시 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 존재하지 않습니다."),
    MISSING_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰 쿠키가 존재하지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,"올바르지 않은 인증 정보입니다."),
    //사용자 관련
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 사용자입니다"),

    //비밀번호 관련
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 일치합니다."),

    //사용자 설정 관련
    USER_AGREED_NOT_FOUND(HttpStatus.NOT_FOUND,"사용자 약관 동의 정보를 찾을 수 없습니다."),
    USER_PREFERENCE_NOT_FOUND(HttpStatus.NOT_FOUND,"사용자 설정을 찾을 수 없습니다."),

    //리프레시 토큰 암호화 관련
    HMAC_256_NOT_AVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR, "HMAC-SHA256이 존재하지 않습니다"),
    INVALID_HMAC_KEY(HttpStatus.INTERNAL_SERVER_ERROR, "유효하지 않은 HMAC KEY입니다."),

    //리프레시 토큰 파싱 관련
    INVALID_REFRESH_TOKEN_JWT(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰을 파싱하려 했습니다."),
    REFRESH_TOKEN_EXPIRED_JWT(HttpStatus.UNAUTHORIZED, "만료된 리프레시 토큰을 파싱하려 했습니다.");

    //권한 관련
//    ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 작업을 수행할 권한이 없습니다.");
    private final HttpStatus status;
    private final String message;
}