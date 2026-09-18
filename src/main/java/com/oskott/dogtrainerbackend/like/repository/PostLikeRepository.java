package com.oskott.dogtrainerbackend.like.repository;

import com.oskott.dogtrainerbackend.like.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    Optional<PostLike> findByPostIdAndUserId(UUID postId, UUID userId);

    long countByPostId(UUID postId);

    /**
     * Bulk count per post, used when enriching a page of posts to avoid one query per post.
     */
    @Query("select l.postId as postId, count(l) as likeCount from PostLike l where l.postId in :postIds group by l.postId")
    List<PostIdCount> countByPostIdIn(@Param("postIds") List<UUID> postIds);

    /**
     * The subset of {@code postIds} that {@code userId} has liked, used to compute
     * {@code likedByMe} for a page of posts without one query per post.
     */
    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    List<UUID> findLikedPostIds(@Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);

    interface PostIdCount {
        UUID getPostId();
        long getLikeCount();
    }
}
