package com.oskott.dogtrainerbackend.comment.service;

import com.oskott.dogtrainerbackend.comment.dto.CommentResponse;
import com.oskott.dogtrainerbackend.comment.dto.CreateCommentRequest;
import com.oskott.dogtrainerbackend.comment.entity.Comment;
import com.oskott.dogtrainerbackend.comment.repository.CommentRepository;
import com.oskott.dogtrainerbackend.common.exception.AccessDeniedForResourceException;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.post.entity.Post;
import com.oskott.dogtrainerbackend.post.repository.PostRepository;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public CommentResponse createComment(UUID postId, CreateCommentRequest request) {
        if (!postRepository.existsById(postId)) {
            throw ResourceNotFoundException.forEntity("Post", postId);
        }
        UUID authorId = currentUserProvider.getCurrentUserId();
        Comment comment = new Comment(UUID.randomUUID(), postId, authorId, request.content(), Instant.now());
        commentRepository.save(comment);
        return toResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(UUID postId) {
        if (!postRepository.existsById(postId)) {
            throw ResourceNotFoundException.forEntity("Post", postId);
        }
        List<Comment> comments = commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId);
        Map<UUID, User> authorsById = new HashMap<>();
        userRepository.findAllById(comments.stream().map(Comment::getAuthorId).distinct().toList())
                .forEach(user -> authorsById.put(user.getId(), user));
        return comments.stream().map(comment -> toResponse(comment, authorsById)).toList();
    }

    /**
     * The comment's author, or the post's own author, may delete it - same moderation-style
     * ownership rule as reporting/blocking content on someone's post.
     */
    @Transactional
    public void deleteComment(UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Comment", commentId));
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        if (!comment.getAuthorId().equals(currentUserId)) {
            Post post = postRepository.findById(comment.getPostId())
                    .orElseThrow(() -> ResourceNotFoundException.forEntity("Post", comment.getPostId()));
            if (!post.getAuthorId().equals(currentUserId)) {
                throw new AccessDeniedForResourceException("You do not have access to this comment");
            }
        }
        commentRepository.delete(comment);
    }

    /**
     * Bulk comment counts for a page of posts, keyed by post id. Posts with no comments are
     * omitted from the map - callers should default to 0.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> countByPostIds(List<UUID> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        commentRepository.countByPostIdIn(postIds).forEach(row -> counts.put(row.getPostId(), row.getCommentCount()));
        return counts;
    }

    private CommentResponse toResponse(Comment comment) {
        Map<UUID, User> authorsById = new HashMap<>();
        userRepository.findById(comment.getAuthorId()).ifPresent(user -> authorsById.put(user.getId(), user));
        return toResponse(comment, authorsById);
    }

    private CommentResponse toResponse(Comment comment, Map<UUID, User> authorsById) {
        User author = authorsById.get(comment.getAuthorId());
        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId(),
                author != null ? author.getName() : null,
                author != null ? author.getAvatarUrl() : null,
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
