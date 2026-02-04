package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserNotification;
import dartoo.accountService.domain.enums.NotificationStatus;
import dartoo.accountService.dto.core.NotificationCreateRequest;
import dartoo.accountService.dto.core.NotificationListResponse;
import dartoo.accountService.dto.core.NotificationResponse;
import dartoo.accountService.error.ApiException;
import dartoo.accountService.repository.UserEntityRepository;
import dartoo.accountService.repository.core.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static dartoo.accountService.error.ErrorCode.NOTIFICATION_NOT_FOUND;
import static dartoo.accountService.error.ErrorCode.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationService {
    //90일까지는 소프트 삭제, 이후 넘기면 DB에서도 삭제하는 하드 삭제
    private static final Duration NOTIFICATION_TTL = Duration.ofDays(90);

    private final UserNotificationRepository userNotificationRepository;
    private final UserEntityRepository userEntityRepository;

    //DB에 알림 추가
    public NotificationResponse addNotification(NotificationCreateRequest request){
        UserEntity user = getCurrentUser();
        UserNotification saved = userNotificationRepository.save(
                UserNotification.builder()
                        .user(user)
                        .title(request.getTitle())
                        .content(request.getContent())
                        .status(NotificationStatus.UNREAD)
                        .build()
        );
        return NotificationResponse.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .content(saved.getContent())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    //DB에 알림 읽음으로 표시
    public NotificationResponse markAsRead(Long id){
        UserEntity user = getCurrentUser();
        UserNotification notification = userNotificationRepository.findByIdAndUserId(id,user.getId())
                .orElseThrow(()->new ApiException(NOTIFICATION_NOT_FOUND));
        notification.markRead(Instant.now());
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .status(notification.getStatus())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }

    //알림 전체 리스트 가져오기
    @Transactional(readOnly = true)
    public NotificationListResponse readAll(){
        UserEntity user = getCurrentUser();
        Instant deadline = Instant.now().minus(NOTIFICATION_TTL);

        //삭제 처리가 되지 않은 90일 이내의 알림을 내림차순으로 정렬해서 불러오기
        List<UserNotification> notifications = userNotificationRepository
                .findAllByUserIdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(user.getId(),deadline,NotificationStatus.DELETED);

        return NotificationListResponse.builder()
                .notificationList(notifications.stream().map(n->NotificationResponse.builder()
                        .id(n.getId())
                        .title(n.getTitle())
                        .content(n.getContent())
                        .status(n.getStatus())
                        .createdAt(n.getCreatedAt())
                        .readAt(n.getReadAt())
                        .build()).toList())
                .build();
    }

    //알림 삭제 (소프트 삭제)
    public void delete(Long id){
        UserEntity user = getCurrentUser();
        UserNotification notification = userNotificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(()->new ApiException(NOTIFICATION_NOT_FOUND));
        //소프트 삭제
        notification.markDeleted();
        log.info("User {} deleted notification id #{} - {}.",getSessionEmail(),id,notification.getTitle());
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
