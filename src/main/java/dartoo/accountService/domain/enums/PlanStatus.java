package dartoo.accountService.domain.enums;

public enum PlanStatus {
    ACTIVE, CANCELLED, EXPIRED, REFUNDED
}
/*
ACTIVE: 정상 사용 중
CANCELLED: 취소는 됐지만 아직 기간 남아서 사용 중
EXPIRED: 이제 기간도 끝나서 사용 불가
REFUNDED: 아예 환불돼서 끝난 상태.
 */