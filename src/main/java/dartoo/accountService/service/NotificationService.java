package dartoo.accountService.service;

import dartoo.accountService.domain.UserEntity;
import dartoo.accountService.domain.UserNotification;
import dartoo.accountService.domain.enums.NotificationStatus;
import dartoo.accountService.domain.enums.NotificationType;
import dartoo.accountService.dto.core.NotificationListResponse;
import dartoo.accountService.dto.core.NotificationResponse;
import dartoo.accountService.dto.internal.BulkNotificationResponse;
import dartoo.accountService.dto.internal.InternalNotificationCreateRequest;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    //내부 서비스 호출을 통한 알림 일괄 생성 (SecurityContext 사용 금지)
    public BulkNotificationResponse bulkCreateInternal(List<InternalNotificationCreateRequest> requests) {
        int total = requests.size();
        int skipped = 0;

        // userId 파싱 — 유효한 Long 값만 수집 (중복 userId 허용)
        Set<Long> userIds = new HashSet<>();
        for (InternalNotificationCreateRequest req : requests) {
            try {
                userIds.add(Long.parseLong(req.getUserId()));
            } catch (NumberFormatException e) {
                log.warn("Skip notification: invalid userId format '{}'", req.getUserId());
            }
        }
        if (userIds.isEmpty()) {
            return BulkNotificationResponse.builder()
                    .total(total).created(0).skipped(total).build();
        }

        // 배치 조회로 N+1 해소 (N회 findById → 1회 findAllById)
        Map<Long, UserEntity> userMap = userEntityRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(UserEntity::getId, u -> u));

        // 원본 리스트를 순회하며 알림 생성 (동일 userId 다중 알림 지원)
        List<UserNotification> toSave = new ArrayList<>();
        for (InternalNotificationCreateRequest req : requests) {
            Long userId;
            try {
                userId = Long.parseLong(req.getUserId());
            } catch (NumberFormatException e) {
                skipped++;
                continue; // 이미 위에서 경고 로그 출력
            }
            UserEntity user = userMap.get(userId);
            if (user == null) {
                log.warn("Skip notification: user not found for id={}", userId);
                skipped++;
                continue;
            }
            NotificationType type;
            String eventType = req.getEventType();
            if ("disclosure.created".equals(eventType) || "disclosure.updated".equals(eventType)) {
                type = NotificationType.DISCLOSURE_UPDATE;
            } else if ("summary.updated".equals(eventType)) {
                type = NotificationType.AI_SUMMARY;
            } else {
                log.warn("Skip notification: unknown eventType '{}'", eventType);
                skipped++;
                continue;
            }
            toSave.add(UserNotification.builder()
                    .user(user)
                    .title(req.getTitle())
                    .receptNo(req.getReceptNo())
                    .corpName(req.getCorpName())
                    .corpCode(req.getCorpCode())
                    .eventType(type)
                    .summaryLines(req.getSummaryLines())
                    .status(NotificationStatus.UNREAD)
                    .build());
        }
        if (!toSave.isEmpty()) {
            userNotificationRepository.saveAll(toSave);
        }
        return BulkNotificationResponse.builder()
                .total(total).created(toSave.size()).skipped(skipped).build();
    }

    //DB에 알림 읽음으로 표시
    public NotificationResponse markAsRead(Long id){
        UserEntity user = getCurrentUser();
        UserNotification notification = userNotificationRepository.findByIdAndUser_Id(id,user.getId())
                .orElseThrow(()->new ApiException(NOTIFICATION_NOT_FOUND));
        notification.markRead(Instant.now());
        return NotificationResponse.builder()
                .id(notification.getId())
                .receptNo(notification.getReceptNo())
                .type(notification.getEventType())
                .corpName(notification.getCorpName())
                .corpCode(notification.getCorpCode())
                .title(notification.getTitle())
                .status(notification.getStatus())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .summaryLines(notification.getSummaryLines())
                .build();
    }

    //알림 전체 리스트 가져오기
    @Transactional(readOnly = true)
    public NotificationListResponse readAll(){
        UserEntity user = getCurrentUser();
        Instant deadline = Instant.now().minus(NOTIFICATION_TTL);

        //삭제 처리가 되지 않은 90일 이내의 알림을 내림차순으로 정렬해서 불러오기
        List<UserNotification> notifications = userNotificationRepository
                .findAllByUser_IdAndCreatedAtAfterAndStatusNotOrderByCreatedAtDesc(user.getId(),deadline,NotificationStatus.DELETED);

        return NotificationListResponse.builder()
                .notificationList(notifications.stream().map(n->NotificationResponse.builder()
                        .id(n.getId())
                        .receptNo(n.getReceptNo())
                        .type(n.getEventType())
                        .corpName(n.getCorpName())
                        .corpCode(n.getCorpCode())
                        .title(n.getTitle())
                        .status(n.getStatus())
                        .createdAt(n.getCreatedAt())
                        .readAt(n.getReadAt())
                        .summaryLines(n.getSummaryLines())
                        .build()).toList())
                .build();
    }

    //알림 삭제 (소프트 삭제)
    public void deleteOne(Long id){
        UserEntity user = getCurrentUser();
        UserNotification notification = userNotificationRepository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(()->new ApiException(NOTIFICATION_NOT_FOUND));
        //소프트 삭제
        notification.markDeleted();
        log.info("User {} deleted notification id #{} - {}.",getSessionEmail(),id,notification.getTitle());
    }

    //알림 모두 삭제 (소프트 삭제)
    public void deleteAll(){
        UserEntity user = getCurrentUser();

        //한번에 벌크 쿼리로 처리
        Instant deadline = Instant.now().minus(NOTIFICATION_TTL);
        int notifications = userNotificationRepository.softDeleteAllVisible(user.getId(),deadline,NotificationStatus.DELETED);

        log.info("User {} deleted total {} number of notifications",getSessionEmail(),notifications);
    }

    private String getSessionEmail(){
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    public UserEntity getCurrentUser(){
        return userEntityRepository.findByUserEmail(getSessionEmail())
                .orElseThrow(()->new ApiException(USER_NOT_FOUND));
    }
}
