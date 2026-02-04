package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserSearchHistory;
import dartoo.accountService.dto.core.SearchHistoryCreateRequest;
import dartoo.accountService.dto.core.SearchHistoryListResponse;
import dartoo.accountService.dto.core.SearchHistoryResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserSearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static dartoo.accountService.error.ErrorCode.HISTORY_NOT_FOUND;
import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SearchHistoryService {

    //90일 이상된 검색어 제거, 30개 이상 저장하지 않도록 지정 -> "정책 변경 시 수정"
    private static final int MAX_SEARCH_HISTORY_COUNT = 30;
    private static final Duration SEARCH_HISTORY_TTL = Duration.ofDays(90);

    private final UserSearchHistoryRepository userSearchHistoryRepository;
    private final UserEntityRepository userEntityRepository;

    //검색 기록에 추가
    public SearchHistoryResponse addHistory(SearchHistoryCreateRequest request){
        UserEntity user = getCurrentUser();
        Instant now = Instant.now();

        //기존의 검색 기록 찾기 -> 존재하는 경우, 검색시각 업데이트, 없으면 새롭게 생성
        UserSearchHistory history = userSearchHistoryRepository.findByUserIdAndQuery(user.getId(),request.getQuery())
                .map( searched->{
                    searched.search(now);
                    return searched;
                })
                .orElseGet(()-> UserSearchHistory.builder()
                        .user(user)
                        .query(request.getQuery())
                        .searchedAt(now)
                        .build()
                );

        //업데이트한 정보 저장
        UserSearchHistory saved = userSearchHistoryRepository.save(history);
        //저장 후 검색 기록 정리
        cleanup(user.getId());
        return SearchHistoryResponse.builder()
                .historyId(saved.getId())
                .query(saved.getQuery())
                .searchedAt(saved.getSearchedAt())
                .build();
    }

    //검색 기록 전체 읽기
    //@param int page -> 최근 검색어 중 총 몇개를 불러올 지, 없으면 30개 조회
    @Transactional(readOnly = true)
    public SearchHistoryListResponse readHistory(int page){
        UserEntity user = getCurrentUser();
        cleanup(user.getId());
        List<UserSearchHistory> histories = userSearchHistoryRepository
                .findAllByUserIdOrderBySearchedAtDesc(user.getId(), PageRequest.of(0,page));
        return SearchHistoryListResponse.builder()
                .historyList(histories.stream().map(h -> SearchHistoryResponse.builder()
                                .historyId(h.getId())
                                .query(h.getQuery())
                                .searchedAt(h.getSearchedAt())
                                .build())
                        .collect(Collectors.toList())).build();
    }

    //특정 검색 기록만 제거
    public void deleteOne(Long historyId){
        UserEntity user = getCurrentUser();
        UserSearchHistory history = userSearchHistoryRepository.findByIdAndUserId(historyId,user.getId())
                .orElseThrow(()->new ApiException(HISTORY_NOT_FOUND));

        userSearchHistoryRepository.delete(history);
        log.info("User {} deleted search history id #{} - {}.",getSessionEmail(),historyId,history.getQuery());
    }

    //검색 기록 전체 제거
    public void deleteAll(){
        UserEntity user = getCurrentUser();
        Long amount = userSearchHistoryRepository.deleteAllByUserId(user.getId());
        log.info("User {} deleted {} search histories.",getSessionEmail(),amount);
    }

    private void cleanup(Long id) {
        //90일 이전 검색 기록 삭제
        Instant deadline = Instant.now().minus(SEARCH_HISTORY_TTL);
        userSearchHistoryRepository.deleteBySearchedAtBefore(deadline);
        //그래도 30개가 넘을 경우, 나머지 삭제해서 일정 수량 유지
        List<UserSearchHistory> all = userSearchHistoryRepository.findAllByUserIdOrderBySearchedAtDesc(id);
        if(all.size()>MAX_SEARCH_HISTORY_COUNT){
            userSearchHistoryRepository.deleteAll(all.subList(MAX_SEARCH_HISTORY_COUNT,all.size()));
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
