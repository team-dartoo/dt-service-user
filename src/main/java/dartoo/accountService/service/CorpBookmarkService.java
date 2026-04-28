package dartoo.accountService.service;

import dartoo.accountService.domain.UserCorpBookmark;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.dto.core.BookmarkCreateRequest;
import dartoo.accountService.dto.core.BookmarkListResponse;
import dartoo.accountService.dto.core.BookmarkReorderRequest;
import dartoo.accountService.dto.core.BookmarkResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserCorpBookmarkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dartoo.accountService.error.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CorpBookmarkService {

    private final UserCorpBookmarkRepository userCorpBookmarkRepository;
    private final UserEntityRepository userEntityRepository;

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

    //기존 행에 displayOrder가 없으면(=null) createdAt DESC, id ASC 순으로 채워넣는 backfill
    private List<UserCorpBookmark> backfillDisplayOrder(Long userId, List<UserCorpBookmark> bookmarks) {
        boolean needsBackfill = bookmarks.stream().anyMatch(b -> b.getDisplayOrder() == null);
        if (!needsBackfill) return bookmarks;

        List<UserCorpBookmark> unordered = userCorpBookmarkRepository
                .findAllByUser_IdAndDisplayOrderIsNullOrderByCreatedAtDescIdAsc(userId);
        for (int i = 0; i < unordered.size(); i++) {
            userCorpBookmarkRepository.updateDisplayOrder(userId, unordered.get(i).getCorpCode(), i);
        }

        log.info("Backfilled displayOrder for {} bookmarks of user {}", unordered.size(), userId);
        return userCorpBookmarkRepository.findAllByUser_IdOrderByDisplayOrderAscIdAsc(userId);
    }

    //사용자의 전체 북마크 목록을 리턴하는 메서드 (displayOrder ASC)
    public BookmarkListResponse listCorpBookmark(){
        UserEntity user = getCurrentUser();

        List<UserCorpBookmark> bookmarks = backfillDisplayOrder(
                user.getId(),
                userCorpBookmarkRepository.findAllByUser_IdOrderByDisplayOrderAscIdAsc(user.getId())
        );

        List<BookmarkResponse> corpList = new ArrayList<>();

        for(UserCorpBookmark bookmark : bookmarks) {
            BookmarkResponse response = BookmarkResponse.builder()
                    .corpCode(bookmark.getCorpCode())
                    .corpName(bookmark.getCorpName())
                    .createdAt(bookmark.getCreatedAt())
                    .build();

            corpList.add(response);
        }

        return BookmarkListResponse.builder()
                .corpList(corpList)
                .build();
    }

    //DB에 사용자의 신규 북마크 추가
    public BookmarkResponse addCorpBookmark(BookmarkCreateRequest request){
        UserEntity user = getCurrentUser();

        if(userCorpBookmarkRepository.existsByUser_IdAndCorpCode(user.getId(),request.getCorpCode())){
            throw new ApiException(DUPLICATE_BOOKMARK);
        }

        Integer maxOrder = userCorpBookmarkRepository.findMaxDisplayOrderByUser_Id(user.getId());
        int nextOrder = (maxOrder != null ? maxOrder : -1) + 1;

        UserCorpBookmark saved = userCorpBookmarkRepository.save(
                UserCorpBookmark.builder()
                        .user(user)
                        .corpCode(request.getCorpCode())
                        .corpName(request.getCorpName())
                        .displayOrder(nextOrder)
                        .build()
        );

        return BookmarkResponse.builder()
                .corpCode(saved.getCorpCode())
                .corpName(saved.getCorpName())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    //사용자의 북마크를 삭제
    //관리자가 삭제하게 만들어야 할 경우, userId를 인수로 받든지 해야할 것 같다.
    public void deleteBookmark(String corpCode){
        UserEntity user = getCurrentUser();

        long deleted = userCorpBookmarkRepository.deleteByUser_IdAndCorpCode(user.getId(),corpCode);
        if(deleted==0){
            throw new ApiException(BOOKMARK_NOT_FOUND);
        }
        log.info("User {} deleted bookmark id #{} - {}.",getSessionEmail(),deleted,corpCode);
    }

    //전체 순서를 한 번에 업데이트 (PUT /reorder)
    public void reorderBookmarks(BookmarkReorderRequest request){
        UserEntity user = getCurrentUser();
        Long userId = user.getId();

        List<UserCorpBookmark> bookmarks = userCorpBookmarkRepository
                .findAllByUser_IdOrderByDisplayOrderAscIdAsc(userId);

        Set<String> requested = new HashSet<>(request.getCorpCodes());

        for (int i = 0; i < request.getCorpCodes().size(); i++) {
            String corpCode = request.getCorpCodes().get(i);
            userCorpBookmarkRepository.updateDisplayOrder(userId, corpCode, i);
        }

        int offset = request.getCorpCodes().size();
        int extra = 0;
        for (UserCorpBookmark b : bookmarks) {
            if (!requested.contains(b.getCorpCode())) {
                userCorpBookmarkRepository.updateDisplayOrder(userId, b.getCorpCode(), offset + extra);
                extra++;
            }
        }

        log.info("User {} reordered {} bookmarks.", getSessionEmail(), request.getCorpCodes().size());
    }

//    public void adminDeleteBookmark(Long userId, String corpId) {
//        long deleted = userCorpBookmarkRepository.deleteByUserIdAndCorpId(userId, corpId);
//        if (deleted == 0) {
//            throw new ApiException(BOOKMARK_NOT_FOUND);
//        }
//    }
}
