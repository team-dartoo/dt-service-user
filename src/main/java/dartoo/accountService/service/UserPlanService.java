package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserPlan;
import dartoo.accountService.dto.core.PlanHistoryListResponse;
import dartoo.accountService.dto.core.PlanHistoryResponse;
import dartoo.accountService.dto.core.PlanResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserPlanService {

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
        List<UserPlan> plans = userPlanRepository.findAllByUserIdOrderByStartAtDesc(user.getId());
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
