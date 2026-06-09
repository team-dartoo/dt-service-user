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
    REFRESH_TOKEN_ALREADY_REVOKED(HttpStatus.UNAUTHORIZED, "이미 철회된 리프레시 토큰입니다."),
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
    USER_BIRTHDAY_CANNOT_BE_FUTURE(HttpStatus.BAD_REQUEST, "생일은 미래일 수 없습니다."),

    //리프레시 토큰 암호화 관련
    HMAC_256_NOT_AVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR, "HMAC-SHA256이 존재하지 않습니다"),
    INVALID_HMAC_KEY(HttpStatus.INTERNAL_SERVER_ERROR, "유효하지 않은 HMAC KEY입니다."),

    //리프레시 토큰 파싱 관련
    INVALID_REFRESH_TOKEN_JWT(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰을 파싱하려 했습니다."),
    REFRESH_TOKEN_EXPIRED_JWT(HttpStatus.UNAUTHORIZED, "만료된 리프레시 토큰을 파싱하려 했습니다."),

    //OAuth 관련
    INVALID_PROVIDER_ID(HttpStatus.BAD_REQUEST, "유효하지 않은 형식의 OAuth Provider ID입니다."),
    USER_ALREADY_ONBOARDED(HttpStatus.CONFLICT,"이미 온보딩이 완료된 계정입니다."),
    OAuth_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT,"이미 해당 계정으로 생성된 OAuth 계정이 있습니다."),

    //권한 관련
//    ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 작업을 수행할 권한이 없습니다.");

    //북마크 관련
    DUPLICATE_BOOKMARK(HttpStatus.CONFLICT,"이미 추가된 북마크입니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND,"찾을 수 없는 북마크입니다."),

    //검색 기록 관련
    HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND,"존재하지 않는 검색 기록입니다."),

    //사용자 알림 관련
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND,"존재하지 않는 알림입니다."),
    INVALID_NOTIFICATION_TYPE(HttpStatus.BAD_REQUEST, "유효하지 않은 알림 타입입니다."),
    INVALID_SERVICE_API_KEY(HttpStatus.UNAUTHORIZED, "유효하지 않은 서비스 API 키입니다."),

    //플랜 관련
    INVALID_UPDATE_PLAN_ACTION(HttpStatus.BAD_REQUEST, "잘못된 플랜 업데이트 방식입니다."),
    INVALID_PLAN_UPDATE_REQUEST(HttpStatus.BAD_REQUEST, "구독/구독연장 plan은 PREMIUM만 가능합니다."),
    INVALID_PLAN_DURATION(HttpStatus.BAD_REQUEST,"SUBSCRIBE/RENEW 요청에는 duration이 필수입니다."),
    PLAN_TO_CANCEL_NOT_FOUND(HttpStatus.NOT_FOUND,"취소할 플랜을 찾을 수 없습니다."),
    INVALID_RENEW_REQUEST(HttpStatus.BAD_REQUEST,"잘못된 갱신 요청입니다"),
    ALREADY_RENEWED(HttpStatus.CONFLICT, "이미 갱신된 플랜입니다"),
    RENEW_NOT_ALLOWED_YET(HttpStatus.BAD_REQUEST, "아직 연장 가능한 기간이 아닙니다."),
    INVALID_RENEW_DURATION(HttpStatus.BAD_REQUEST, "잘못된 연장 기간입니다"),
    ALREADY_SUBSCRIBED(HttpStatus.CONFLICT,"이미 구독한 사용자는 구독을 갱신해야 합니다."),
    TRIAL_ALREADY_USED(HttpStatus.CONFLICT,"이미 TRIAL을 사용한 사용자입니다."),
    TRIAL_NOT_ALLOWED_FOR_EXISTING_CUSTOMER(HttpStatus.FORBIDDEN,"이미 유료 요금제 사용 경력이 있는 사용자입니다."),

    //외부 서비스 연동 관련
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 연동 중 오류가 발생했습니다."),

    //RevenueCat 관련
    INVALID_WEBHOOK_SECRET(HttpStatus.UNAUTHORIZED,"유효하지 않은 Webhook Secret입니다."),
    INVALID_PRODUCT_ID(HttpStatus.BAD_REQUEST,"등록되지 않은 RevenueCat product_id입니다."),
    REFUND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,"RevenueCat 환불 API 호출에 실패했습니다."),
    APPLE_REFUND_REQUIRED(HttpStatus.METHOD_NOT_ALLOWED,"App Store 결제된 플랜의 경우에는 인 앱 환불이 불가합니다.");

    private final HttpStatus status;
    private final String message;
}