package com.oskott.dogtrainerbackend.comment.repository;

import com.oskott.dogtrainerbackend.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findAllByPostIdOrderByCreatedAtAsc(UUID postId);

    /**
     * Bulk count per post, used when enriching a page of posts to avoid one query per post.
     */
    @Query("select c.postId as postId, count(c) as commentCount from Comment c where c.postId in :postIds group by c.postId")
    List<PostIdCount> countByPostIdIn(@Param("postIds") List<UUID> postIds);

    interface PostIdCount {
        UUID getPostId();
        long getCommentCount();
    }
}
