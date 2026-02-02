package dartoo.accountService.service;

import dartoo.accountService.domain.UserCorpBookmark;
import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.enums.Role;
import dartoo.accountService.dto.core.BookmarkCreateRequest;
import dartoo.accountService.dto.core.BookmarkListResponse;
import dartoo.accountService.dto.core.BookmarkResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserCorpBookmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static dartoo.accountService.error.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional
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

    //사용자의 전체 북마크 목록을 리턴하는 메서드
    public BookmarkListResponse listCorpBookmark(){
        UserEntity user = getCurrentUser();

        List<UserCorpBookmark> bookmarks = userCorpBookmarkRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        List<BookmarkResponse> corpList = new ArrayList<>();

        for(UserCorpBookmark bookmark : bookmarks) {
            BookmarkResponse response = BookmarkResponse.builder()
                    .corpId(bookmark.getCorpId())
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

        if(userCorpBookmarkRepository.existsByUserIdAndCorpId(user.getId(),request.getCorpId())){
            throw new ApiException(DUPLICATE_BOOKMARK);
        }

        UserCorpBookmark saved = userCorpBookmarkRepository.save(
                UserCorpBookmark.builder()
                        .user(user)
                        .corpId(request.getCorpId())
                        .corpName(request.getCorpName())
                        .build()
        );

        return BookmarkResponse.builder()
                .corpId(saved.getCorpId())
                .corpName(saved.getCorpName())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    //권한 확인 후, 사용자의 북마크를 삭제
    public void deleteBookmark(String corpId){
        UserEntity user = getCurrentUser();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String sessionUsername = auth.getName();

        //요청자가 본인인지, 요청자의 역할이 관리자인지 확인
        boolean isOwner = sessionUsername.equals(user.getUserEmail());
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(role -> role.getAuthority().equals("ROLE_"+Role.ADMIN.name()));

        if(!isOwner && !isAdmin){
            throw new AccessDeniedException("북마크를 삭제할 권한이 없습니다.");
        }

        long deleted = userCorpBookmarkRepository.deleteByUserIdAndCorpId(user.getId(),corpId);
        if(deleted==0){
            throw new ApiException(BOOKMARK_NOT_FOUND);
        }
    }
}
