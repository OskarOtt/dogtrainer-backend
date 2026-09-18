package com.oskott.dogtrainerbackend.moderation.service;

import com.oskott.dogtrainerbackend.common.exception.BusinessRuleException;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.follow.service.FollowService;
import com.oskott.dogtrainerbackend.moderation.dto.CreateReportRequest;
import com.oskott.dogtrainerbackend.moderation.entity.Block;
import com.oskott.dogtrainerbackend.moderation.entity.Report;
import com.oskott.dogtrainerbackend.moderation.repository.BlockRepository;
import com.oskott.dogtrainerbackend.moderation.repository.ReportRepository;
import com.oskott.dogtrainerbackend.post.entity.Post;
import com.oskott.dogtrainerbackend.post.repository.PostRepository;
import com.oskott.dogtrainerbackend.user.dto.PublicUserResponse;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Content reporting and user blocking. Reports are persisted only - there's no moderation UI yet,
 * they're reviewed manually. Blocking removes any existing follow relationship both ways and its
 * effects (hidden posts, no following) are applied both ways too, regardless of who blocked whom.
 */
@Service
public class ModerationService {

    private static final Set<String> VALID_REASONS = Set.of(
            "Spam", "Inappropriate content", "Harassment or bullying", "Animal welfare concern", "Other");

    private final ReportRepository reportRepository;
    private final BlockRepository blockRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FollowService followService;
    private final CurrentUserProvider currentUserProvider;

    public ModerationService(
            ReportRepository reportRepository,
            BlockRepository blockRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            FollowService followService,
            CurrentUserProvider currentUserProvider
    ) {
        this.reportRepository = reportRepository;
        this.blockRepository = blockRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.followService = followService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public void createReport(CreateReportRequest request) {
        if (request.postId() == null && request.reportedUserId() == null) {
            throw new BusinessRuleException("A report must reference a post or a user");
        }
        if (!VALID_REASONS.contains(request.reason())) {
            throw new BusinessRuleException("Unsupported report reason: " + request.reason());
        }
        UUID reporterId = currentUserProvider.getCurrentUserId();
        UUID reportedUserId = request.reportedUserId();
        if (request.postId() != null) {
            Post post = postRepository.findById(request.postId())
                    .orElseThrow(() -> ResourceNotFoundException.forEntity("Post", request.postId()));
            reportedUserId = reportedUserId != null ? reportedUserId : post.getAuthorId();
        }
        reportRepository.save(new Report(
                UUID.randomUUID(), reporterId, request.postId(), reportedUserId, request.reason(), request.details(), Instant.now()));
    }

    @Transactional
    public void blockUser(UUID blockedId) {
        UUID blockerId = currentUserProvider.getCurrentUserId();
        if (blockerId.equals(blockedId)) {
            throw new BusinessRuleException("You cannot block yourself");
        }
        if (!userRepository.existsById(blockedId)) {
            throw ResourceNotFoundException.forEntity("User", blockedId);
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new BusinessRuleException("You have already blocked this user");
        }
        blockRepository.save(new Block(UUID.randomUUID(), blockerId, blockedId, Instant.now()));
        followService.removeMutualFollow(blockerId, blockedId);
    }

    @Transactional
    public void unblockUser(UUID blockedId) {
        UUID blockerId = currentUserProvider.getCurrentUserId();
        Block block = blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Block", blockedId));
        blockRepository.delete(block);
    }

    @Transactional(readOnly = true)
    public List<PublicUserResponse> listBlockedUsers() {
        UUID blockerId = currentUserProvider.getCurrentUserId();
        List<UUID> blockedIds = blockRepository.findAllByBlockerIdOrderByCreatedAtDesc(blockerId).stream()
                .map(Block::getBlockedId)
                .toList();
        return userRepository.findAllById(blockedIds).stream()
                .map(PublicUserResponse::from)
                .toList();
    }

    /**
     * Ids of every user blocked by, or who has blocked, {@code userId} - used by {@code PostService}
     * to hide posts from a blocked/blocking relationship in either direction.
     */
    @Transactional(readOnly = true)
    public List<UUID> getRelatedBlockedUserIds(UUID userId) {
        return blockRepository.findRelatedUserIds(userId);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UUID userA, UUID userB) {
        return blockRepository.existsByBlockerIdAndBlockedId(userA, userB)
                || blockRepository.existsByBlockerIdAndBlockedId(userB, userA);
    }
}
