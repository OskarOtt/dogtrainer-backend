package com.oskott.dogtrainerbackend.follow.service;

import com.oskott.dogtrainerbackend.common.exception.BusinessRuleException;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.follow.entity.Follow;
import com.oskott.dogtrainerbackend.follow.repository.FollowRepository;
import com.oskott.dogtrainerbackend.moderation.repository.BlockRepository;
import com.oskott.dogtrainerbackend.user.dto.PublicUserResponse;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final BlockRepository blockRepository;

    public FollowService(
            FollowRepository followRepository,
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider,
            BlockRepository blockRepository
    ) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.blockRepository = blockRepository;
    }

    @Transactional
    public void follow(UUID followeeId) {
        UUID followerId = currentUserProvider.getCurrentUserId();
        if (followerId.equals(followeeId)) {
            throw new BusinessRuleException("You cannot follow yourself");
        }
        if (!userRepository.existsById(followeeId)) {
            throw ResourceNotFoundException.forEntity("User", followeeId);
        }
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            throw new BusinessRuleException("You are already following this user");
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(followerId, followeeId)
                || blockRepository.existsByBlockerIdAndBlockedId(followeeId, followerId)) {
            throw new BusinessRuleException("You cannot follow this user");
        }
        followRepository.save(new Follow(UUID.randomUUID(), followerId, followeeId, Instant.now()));
    }

    @Transactional
    public void unfollow(UUID followeeId) {
        UUID followerId = currentUserProvider.getCurrentUserId();
        Follow follow = followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Follow", followeeId));
        followRepository.delete(follow);
    }

    @Transactional(readOnly = true)
    public List<PublicUserResponse> listFollowers(UUID userId) {
        List<UUID> followerIds = followRepository.findAllByFolloweeIdOrderByCreatedAtDesc(userId).stream()
                .map(Follow::getFollowerId)
                .toList();
        return userRepository.findAllById(followerIds).stream()
                .map(PublicUserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicUserResponse> listFollowing(UUID userId) {
        List<UUID> followeeIds = getFollowingIds(userId);
        return userRepository.findAllById(followeeIds).stream()
                .map(PublicUserResponse::from)
                .toList();
    }

    /**
     * Used by {@code PostService} to build the feed: the ids of everyone {@code followerId}
     * follows, in no particular order (the feed itself re-sorts posts by recency).
     */
    @Transactional(readOnly = true)
    public List<UUID> getFollowingIds(UUID followerId) {
        return followRepository.findAllByFollowerIdOrderByCreatedAtDesc(followerId).stream()
                .map(Follow::getFolloweeId)
                .toList();
    }

    /**
     * Removes any follow relationship between the two users in either direction. Used by
     * {@code ModerationService} when one user blocks another - a block always implies no more
     * following, both ways.
     */
    @Transactional
    public void removeMutualFollow(UUID userA, UUID userB) {
        followRepository.findByFollowerIdAndFolloweeId(userA, userB).ifPresent(followRepository::delete);
        followRepository.findByFollowerIdAndFolloweeId(userB, userA).ifPresent(followRepository::delete);
    }
}
