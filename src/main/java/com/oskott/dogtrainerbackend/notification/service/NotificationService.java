package com.oskott.dogtrainerbackend.notification.service;

import com.oskott.dogtrainerbackend.comment.entity.Comment;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.i18n.SupportedLocale;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.follow.entity.Follow;
import com.oskott.dogtrainerbackend.follow.repository.FollowRepository;
import com.oskott.dogtrainerbackend.moderation.repository.BlockRepository;
import com.oskott.dogtrainerbackend.notification.dto.NotificationPageResponse;
import com.oskott.dogtrainerbackend.notification.dto.NotificationResponse;
import com.oskott.dogtrainerbackend.notification.entity.Notification;
import com.oskott.dogtrainerbackend.notification.entity.NotificationType;
import com.oskott.dogtrainerbackend.notification.entity.UserDevice;
import com.oskott.dogtrainerbackend.notification.repository.NotificationRepository;
import com.oskott.dogtrainerbackend.notification.repository.UserDeviceRepository;
import com.oskott.dogtrainerbackend.post.entity.Post;
import com.oskott.dogtrainerbackend.post.util.PostCursor;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates and queries in-app notifications. Creation runs inside the caller's transaction, so a
 * notification exists if and only if the action that caused it was committed. Push delivery is
 * deliberately not done here: a {@link PushRequestedEvent} is published and handled after commit
 * by {@link PushDeliveryService}.
 *
 * <p>Depends on repositories rather than {@code FollowService}/{@code ModerationService} because
 * those services call into this one.
 */
@Service
public class NotificationService {

    private static final int DEFAULT_PAGE_LIMIT = 20;
    private static final int MAX_PAGE_LIMIT = 50;

    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserDeviceRepository userDeviceRepository,
            UserRepository userRepository,
            FollowRepository followRepository,
            BlockRepository blockRepository,
            CurrentUserProvider currentUserProvider,
            ApplicationEventPublisher eventPublisher
    ) {
        this.notificationRepository = notificationRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.currentUserProvider = currentUserProvider;
        this.eventPublisher = eventPublisher;
    }

    /** Notifies everyone who follows the post's author, except users blocked either way. */
    @Transactional
    public void notifyNewPost(Post post) {
        UUID authorId = post.getAuthorId();
        User author = userRepository.findById(authorId).orElse(null);
        if (author == null) {
            return;
        }
        Set<UUID> blocked = new HashSet<>(blockRepository.findRelatedUserIds(authorId));
        List<UUID> recipientIds = followRepository.findAllByFolloweeIdOrderByCreatedAtDesc(authorId).stream()
                .map(Follow::getFollowerId)
                .filter(id -> !blocked.contains(id))
                .distinct()
                .toList();
        create(recipientIds, author, NotificationType.NEW_POST, post.getId(), "POST:" + post.getId());
    }

    /** Notifies the post's owner about a comment from someone else. */
    @Transactional
    public void notifyComment(Comment comment, Post post) {
        UUID commenterId = comment.getAuthorId();
        UUID ownerId = post.getAuthorId();
        if (commenterId.equals(ownerId) || isBlockedEitherWay(commenterId, ownerId)) {
            return;
        }
        userRepository.findById(commenterId).ifPresent(commenter ->
                create(List.of(ownerId), commenter, NotificationType.COMMENT, post.getId(), "COMMENT:" + comment.getId()));
    }

    /**
     * The dedupe key only contains the follower, so following, unfollowing and following again
     * never notifies the same user twice.
     */
    @Transactional
    public void notifyNewFollower(UUID followerId, UUID followeeId) {
        if (followerId.equals(followeeId) || isBlockedEitherWay(followerId, followeeId)) {
            return;
        }
        userRepository.findById(followerId).ifPresent(follower ->
                create(List.of(followeeId), follower, NotificationType.NEW_FOLLOWER, followerId, "FOLLOW:" + followerId));
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse listNotifications(String cursor, Integer limit) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        int pageSize = limit == null ? DEFAULT_PAGE_LIMIT : Math.max(1, Math.min(limit, MAX_PAGE_LIMIT));
        PostCursor.Position position = cursor == null || cursor.isBlank() ? null : PostCursor.decode(cursor);
        List<Notification> rows = notificationRepository.findPage(
                currentUserId,
                position != null ? position.createdAt() : null,
                position != null ? position.id() : null,
                PageRequest.of(0, pageSize + 1)
        );
        boolean hasMore = rows.size() > pageSize;
        List<Notification> page = hasMore ? rows.subList(0, pageSize) : rows;

        Map<UUID, User> actors = userRepository.findAllById(
                        page.stream().map(Notification::getActorId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<NotificationResponse> items = page.stream()
                .map(n -> NotificationResponse.from(n, n.getActorId() != null ? actors.get(n.getActorId()) : null))
                .toList();
        Notification last = page.isEmpty() ? null : page.get(page.size() - 1);
        String nextCursor = hasMore && last != null ? PostCursor.encode(last.getCreatedAt(), last.getId()) : null;
        return new NotificationPageResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByRecipientIdAndReadFalse(currentUserProvider.getCurrentUserId());
    }

    /** Another user's notification is indistinguishable from a missing one (404). */
    @Transactional
    public void markRead(UUID notificationId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Notification", notificationId));
        notification.markRead();
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(currentUserProvider.getCurrentUserId());
    }

    /** Called when a post is deleted so notifications never link to a post that no longer exists. */
    @Transactional
    public void deleteForPost(UUID postId) {
        notificationRepository.deleteByReferenceIdAndTypeIn(postId, List.of(NotificationType.NEW_POST, NotificationType.COMMENT));
    }

    /** Called on account deletion; users are anonymized in place so DB cascades never fire. */
    @Transactional
    public void deleteAllForUser(UUID userId) {
        notificationRepository.deleteAllInvolving(userId);
        userDeviceRepository.deleteAllByUserId(userId);
    }

    private boolean isBlockedEitherWay(UUID userA, UUID userB) {
        return blockRepository.existsByBlockerIdAndBlockedId(userA, userB)
                || blockRepository.existsByBlockerIdAndBlockedId(userB, userA);
    }

    private void create(Collection<UUID> candidateIds, User actor, NotificationType type, UUID referenceId, String dedupeKey) {
        if (candidateIds.isEmpty()) {
            return;
        }
        Set<UUID> alreadyNotified = new HashSet<>(notificationRepository.findRecipientIdsWithDedupeKey(dedupeKey, candidateIds));
        Set<UUID> activeRecipients = userRepository.findAllById(candidateIds).stream()
                .filter(user -> user.getDeletedAt() == null)
                .map(User::getId)
                .collect(Collectors.toSet());
        List<UUID> recipientIds = candidateIds.stream()
                .filter(id -> !id.equals(actor.getId()) && activeRecipients.contains(id) && !alreadyNotified.contains(id))
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        Map<UUID, String> locales = localesByUser(recipientIds);
        Instant now = Instant.now();
        List<Notification> notifications = new ArrayList<>(recipientIds.size());
        for (UUID recipientId : recipientIds) {
            NotificationTexts.Text text = NotificationTexts.render(
                    type, locales.getOrDefault(recipientId, SupportedLocale.ENGLISH), actor.getName());
            notifications.add(new Notification(
                    UUID.randomUUID(), recipientId, actor.getId(), type, text.title(), text.body(), referenceId, dedupeKey, now));
        }
        notificationRepository.saveAll(notifications);
        eventPublisher.publishEvent(new PushRequestedEvent(notifications.stream().map(Notification::getId).toList()));
    }

    /** The locale of each recipient's most recently active device; recipients without a device are absent. */
    private Map<UUID, String> localesByUser(Collection<UUID> userIds) {
        Map<UUID, UserDevice> latest = new HashMap<>();
        userDeviceRepository.findAllByUserIdIn(userIds).stream()
                .sorted(Comparator.comparing(UserDevice::getLastSeenAt))
                .forEach(device -> latest.put(device.getUserId(), device));
        Map<UUID, String> locales = new HashMap<>();
        latest.forEach((userId, device) -> locales.put(userId, device.getLocale()));
        return locales;
    }
}