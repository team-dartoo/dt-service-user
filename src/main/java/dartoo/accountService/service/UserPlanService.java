package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPlan;
import dartoo.accountService.domain.enums.PlanStatus;
import dartoo.accountService.domain.enums.PlanType;
import dartoo.accountService.dto.core.*;
import dartoo.accountService.dto.core.enums.PlanAction;
import dartoo.accountService.dto.core.enums.PlanDuration;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.error.ErrorCode;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import static dartoo.accountService.dto.core.enums.PlanDuration.*;
import static dartoo.accountService.error.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserPlanService {

    private static final long TRIAL_PERIOD_DAYS = 7;
    //만료 며칠 전부터 구독 연장이 가능한지 -> 정책적으로 변경 가능
    private static final long RENEW_WINDOW_DAYS_MONTHLY = 14;
    private static final long RENEW_WINDOW_DAYS_YEARLY = 30;

    private final UserPlanRepository userPlanRepository;
    private final UserEntityRepository userEntityRepository;
    private final TokenService tokenService;

    //현재 사용자의 최신 플랜 정보를 반환
    @Transactional(readOnly = true)
    public PlanResponse getCurrentPlan(){
        UserEntity user = getCurrentUser();
        return PlanResponse.builder()
                .plan(user.getPlan())
                .planExpireAt(user.getPlanExpireAt())
                .planStatus(user.getPlanStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public PlanHistoryListResponse getHistory(){
        UserEntity user = getCurrentUser();
        List<UserPlan> plans = userPlanRepository.findAllByUser_IdOrderByStartAtDesc(user.getId());
        return PlanHistoryListResponse.builder()
                .planHisotryList(plans.stream().map(p-> PlanHistoryResponse.builder()
                        .plan(p.getPlan())
                        .status(p.getStatus())
                        .startAt(p.getStartAt())
                        .expireAt(p.getExpireAt())
                        .build())
                        .collect(Collectors.toList())
                )
                .build();
    }

    //RequestDto에 적힌 액션 정보를 통해 플랜 정보를 case by case로 수정한다
    public PlanUpdateResponse updatePlan(PlanUpdateRequest request){
        UserEntity user = getCurrentUser();
        Instant now = Instant.now();
        log.info("플랜 업데이트 요청: userId={}, action={}, plan={}, duration={}",
                user.getId(), request.getAction(), request.getPlan(), request.getDuration());

        if(request.getDuration()==TRIAL) {
            //무료 체험 검증 로직 추가 1 - 이미 무료 체험을 사용한 경우
            boolean usedTrial = userPlanRepository.existsByUser_IdAndDuration(user.getId(), PlanDuration.TRIAL);
            if (usedTrial) throw new ApiException(TRIAL_ALREADY_USED);
            //무료 체험 검증 로직 추가 2 - 이미 유료 결제를 한 경우
            boolean hasPaidHistory = userPlanRepository.existsByUser_IdAndDurationIn(
                    user.getId(), List.of(PlanDuration.MONTHLY, PlanDuration.YEARLY)
            );
            if (hasPaidHistory) throw new ApiException(TRIAL_NOT_ALLOWED_FOR_EXISTING_CUSTOMER);
        }

        switch (request.getAction()){
            case SUBSCRIBE, RENEW -> {
                //requestDto 검증 로직
                //FREE -> PREMIUM만 존재하기 때문에, FREE로 바꾸는건 CANCEL로 처리
                if (request.getPlan() != PlanType.PREMIUM) {
                    throw new ApiException(INVALID_PLAN_UPDATE_REQUEST);
                }
                //DURATION 정보는 필수
                if (request.getDuration() == null) {
                    throw new ApiException(INVALID_PLAN_DURATION);
                }

                //이미 프리미엄 요금제를 쓰고 있는 경우에는 SUBSCRIBE가 아니라 연장을 해야함.
                if (request.getAction() == PlanAction.SUBSCRIBE && user.getPlan() == PlanType.PREMIUM
                        && user.getPlanExpireAt() != null && user.getPlanExpireAt().isAfter(now)) {
                    throw new ApiException(ALREADY_SUBSCRIBED);
                }

                //기존의 구독이 존재한다면 그 구독이 언제 끝나는지를 파악
                Instant currentExpireAt = now; //현재 사이클 만료일
                if(user.getPlan()==PlanType.PREMIUM && user.getPlanExpireAt()!=null && user.getPlanExpireAt().isAfter(now)){
                    UserPlan currentPlan = userPlanRepository
                            .findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                                    user.getId(), now, now, List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED)
                            )
                            .orElseThrow(() -> new ApiException(INVALID_RENEW_REQUEST));
                    currentExpireAt = currentPlan.getExpireAt();
                }

                //구독 갱신(RENEW) 시의 검증 로직
                validateRenewWindowAndUniqueness(user,request.getAction(),request.getDuration(),now,currentExpireAt);

                //새로운 플랜 만료일을 계산
                Instant newExpireAt = calculateNewExpireAt(currentExpireAt, request.getDuration());

                //새로운 요금제 정보 저장
                //RENEW의 경우에는 startAt이 미래 시간이기 때문에, 현재 정보를 검색할 때 영향이 없음
                userPlanRepository.save(UserPlan.builder()
                                .user(user)
                                .plan(request.getPlan())
                                .duration(request.getDuration())
                                .status(PlanStatus.ACTIVE)
                                .startAt(currentExpireAt) //만료되는 시점부터 다시 시작
                                .expireAt(newExpireAt)
                                .build());
                //user 테이블에 업데이트
                user.updatePlan(PlanType.PREMIUM,PlanStatus.ACTIVE,newExpireAt);

                //새로운 정보 기반 토큰 발급
                String newAccessToken = tokenService.createAccessToken(user,now);

                log.info("플랜 업데이트 완료: userId={}, action={}, oldExpireAt={}, newExpireAt={}, status={}",
                        user.getId(), request.getAction(), currentExpireAt, newExpireAt, user.getPlanStatus());

                return PlanUpdateResponse.builder()
                        .action(request.getAction())
                        .plan(PlanType.PREMIUM)
                        .duration(request.getDuration())
                        .status(user.getPlanStatus())
                        .expireAt(user.getPlanExpireAt())
                        .accessToken(newAccessToken)
                        .build();
            }
            case CANCEL -> {
                //현재 유효한 row 중 (미리 결제한 부분 제외) 취소 CANCELLED로 마킹
                UserPlan currentPlan = userPlanRepository
                        .findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                                user.getId(),now,now,List.of(PlanStatus.ACTIVE,PlanStatus.CANCELLED))
                        .orElseThrow(()-> new ApiException(PLAN_TO_CANCEL_NOT_FOUND));

                if (currentPlan.getStatus() == PlanStatus.CANCELLED) {
                    log.info("이미 취소된 플랜입니다: userId={}, planId={}, expireAt={}",
                            user.getId(), currentPlan.getId(), currentPlan.getExpireAt());
                }

                if(currentPlan.getStatus() == PlanStatus.ACTIVE){
                    currentPlan.changeStatus(PlanStatus.CANCELLED);
                }

                //이미 구독 연장한 부분 다 취소하기
                //(현재는 로직이 동일하지만, 나중에 환불 로직이 들어갈 경우, 이를 쉽게 구현하기 위함)
                Instant currentExpireAt = currentPlan.getExpireAt();

                List<UserPlan> futurePlans = userPlanRepository
                        .findAllByUser_IdAndStartAtGreaterThanEqualAndStatusOrderByStartAtAsc(
                                user.getId(),currentExpireAt,PlanStatus.ACTIVE
                        );
                log.info("미래 연장분 취소 처리: userId={}, currentExpireAt={}, futureCount={}",
                        user.getId(), currentExpireAt, futurePlans.size());

                for(UserPlan futurePlan : futurePlans){
                    if(!futurePlan.getId().equals(currentPlan.getId())){
                        //여기에 환불 로직이 들어가면 될듯
                        futurePlan.changeStatus(PlanStatus.CANCELLED);
                    }
                }

                //userEntity 테이블에 있는 가장 최신 구독 column 정보를 업데이트
                //프리미엄으로 해도 바로 만료되는게 아니니까 PlanType은 PREMIUM으로 유지
                user.updatePlan(PlanType.PREMIUM,PlanStatus.CANCELLED,currentExpireAt);

                //업데이트된 정보 기반으로 토큰 발급
                String newAccessToken = tokenService.createAccessToken(user,now);

                log.info("플랜 취소 완료: userId={}, currentExpireAt={}, userPlanStatus={}, userExpireAt={}",
                        user.getId(), currentExpireAt, user.getPlanStatus(), user.getPlanExpireAt());
                return PlanUpdateResponse.builder()
                        .action(PlanAction.CANCEL)
                        .plan(user.getPlan())
                        .duration(null)
                        .status(user.getPlanStatus())
                        .expireAt(user.getPlanExpireAt())
                        .accessToken(newAccessToken)
                        .build();
            }
            default -> throw new ApiException(INVALID_UPDATE_PLAN_ACTION);
        }
    }

    private Instant calculateNewExpireAt(Instant currentExpireAt, PlanDuration duration) {
        return switch (duration) {
            case MONTHLY -> currentExpireAt.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
            case YEARLY  -> currentExpireAt.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
            case TRIAL   -> currentExpireAt.plus(TRIAL_PERIOD_DAYS, ChronoUnit.DAYS);
        };
    }

    private void validateRenewWindowAndUniqueness(UserEntity user,
                                                  PlanAction action,
                                                  PlanDuration duration,
                                                  Instant now,
                                                  Instant currentExpireAt){

        // SUBSCRIBE는 신규 결제로 보고, RENEW만 정책 검증 수행
        if (action!=PlanAction.RENEW) {
            return;
        }

        //현재 플랜이 없으면 RENEW가 아니라 SUBSCRIBE를 해야하니까
        if(user.getPlan()==PlanType.FREE){
            throw new ApiException(INVALID_RENEW_REQUEST);
        }

        //currentExpireAt 방어
        if (currentExpireAt==null||!currentExpireAt.isAfter(now)) {
            throw new ApiException(INVALID_RENEW_REQUEST);
        }

        //연장 중복 금지: currentExpireAt 이후 시작하는 ACTIVE row가 이미 있으면 거절
        boolean hasFutureActivePlan = userPlanRepository
                .existsByUser_IdAndStartAtGreaterThanEqualAndStatus(
                        user.getId(), currentExpireAt, PlanStatus.ACTIVE
                );

        if (hasFutureActivePlan) {
            throw new ApiException(ALREADY_RENEWED);
        }

        //duration별 연장 가능 윈도우 체크
        PlanDuration currentDuration = userPlanRepository.findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                user.getId(),now,now,List.of(PlanStatus.ACTIVE,PlanStatus.CANCELLED))
                .orElseThrow(()-> new ApiException(INVALID_RENEW_REQUEST)).getDuration();

        long windowDays = switch (currentDuration) {
            case MONTHLY -> RENEW_WINDOW_DAYS_MONTHLY;
            case YEARLY -> RENEW_WINDOW_DAYS_YEARLY;
            case TRIAL -> -1; // 정책: 일단 TRIAL은 연장 불가로 처리 (무료 체험 다 끝나고 결제하는게 자연스러우니까)
        };

        if (windowDays < 0) {
            throw new ApiException(INVALID_RENEW_DURATION);
        }

        Instant renewAllowedFrom = currentExpireAt.minus(windowDays, ChronoUnit.DAYS);
        if (now.isBefore(renewAllowedFrom)) {
            // 너무 이르게 연장하려는 경우
            throw new ApiException(RENEW_NOT_ALLOWED_YET);
        }

    }

    private String getSessionEmail(){
        //SecurityContextHolder.getContext().getAuthentication()에
        //JWT 토큰 정보가 저장되도록 AuthenticationManger을설정해야 한다.
        //->SecurityConfig.java 참조
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    public UserEntity getCurrentUser(){
        return userEntityRepository.findByUserEmail(getSessionEmail())
                .orElseThrow(()->new ApiException(USER_NOT_FOUND));
    }
}
/*
자동 결제는 구현하기 어려워서
일단 매번 선결제하는 모델을 기반으로 만들었습니다.
(연장은 만료가 다가오기 직전 1회만 허용)

CANCEL -> 현재 프리미엄 사용 상태를 취소로 표시하고, 만료일까지 사용 가능함.
현재 사이클의 경우에는 이미 결제한 기간까지 사용하고,
미리 RENEW를 통해 구매한 미래의 구독 사이클에 대해서는 전체 취소 후 환불로 구현함.
 */