package com.oskott.dogtrainerbackend.like.service;

import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.like.entity.PostLike;
import com.oskott.dogtrainerbackend.like.repository.PostLikeRepository;
import com.oskott.dogtrainerbackend.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final CurrentUserProvider currentUserProvider;

    public LikeService(PostLikeRepository postLikeRepository, PostRepository postRepository, CurrentUserProvider currentUserProvider) {
        this.postLikeRepository = postLikeRepository;
        this.postRepository = postRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public void like(UUID postId) {
        if (!postRepository.existsById(postId)) {
            throw ResourceNotFoundException.forEntity("Post", postId);
        }
        UUID userId = currentUserProvider.getCurrentUserId();
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            return;
        }
        postLikeRepository.save(new PostLike(UUID.randomUUID(), postId, userId, Instant.now()));
    }

    @Transactional
    public void unlike(UUID postId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        postLikeRepository.findByPostIdAndUserId(postId, userId).ifPresent(postLikeRepository::delete);
    }

    /**
     * Bulk like counts for a page of posts, keyed by post id. Posts with no likes are omitted
     * from the map - callers should default to 0.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> countByPostIds(List<UUID> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        postLikeRepository.countByPostIdIn(postIds).forEach(row -> counts.put(row.getPostId(), row.getLikeCount()));
        return counts;
    }

    /**
     * The subset of {@code postIds} liked by {@code userId}, used to compute {@code likedByMe}
     * for a page of posts.
     */
    @Transactional(readOnly = true)
    public Set<UUID> likedPostIds(UUID userId, List<UUID> postIds) {
        if (postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(postLikeRepository.findLikedPostIds(userId, postIds));
    }
}
