package com.oskott.dogtrainerbackend.post.service;

import com.oskott.dogtrainerbackend.common.exception.AccessDeniedForResourceException;
import com.oskott.dogtrainerbackend.common.exception.BusinessRuleException;
import com.oskott.dogtrainerbackend.common.exception.InvalidFileException;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.dog.entity.Dog;
import com.oskott.dogtrainerbackend.dog.repository.DogRepository;
import com.oskott.dogtrainerbackend.dog.service.DogService;
import com.oskott.dogtrainerbackend.follow.service.FollowService;
import com.oskott.dogtrainerbackend.moderation.service.ModerationService;
import com.oskott.dogtrainerbackend.post.dto.CreatePostFromSessionRequest;
import com.oskott.dogtrainerbackend.post.dto.CreatePostRequest;
import com.oskott.dogtrainerbackend.post.dto.PostMediaConfirmRequest;
import com.oskott.dogtrainerbackend.post.dto.PostPageResponse;
import com.oskott.dogtrainerbackend.post.dto.PostResponse;
import com.oskott.dogtrainerbackend.post.entity.Post;
import com.oskott.dogtrainerbackend.post.repository.PostRepository;
import com.oskott.dogtrainerbackend.post.util.PostCursor;
import com.oskott.dogtrainerbackend.storage.ImageProcessingService;
import com.oskott.dogtrainerbackend.storage.MediaCategory;
import com.oskott.dogtrainerbackend.storage.StorageService;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlRequest;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlResponse;
import com.oskott.dogtrainerbackend.training.dto.TrainingSessionResponse;
import com.oskott.dogtrainerbackend.training.entity.SessionStatus;
import com.oskott.dogtrainerbackend.training.service.TrainingSessionService;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostService {

    private static final int DEFAULT_PAGE_LIMIT = 20;
    private static final int MAX_PAGE_LIMIT = 50;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final DogRepository dogRepository;
    private final DogService dogService;
    private final TrainingSessionService trainingSessionService;
    private final FollowService followService;
    private final ModerationService moderationService;
    private final CurrentUserProvider currentUserProvider;
    private final StorageService storageService;

    public PostService(
            PostRepository postRepository,
            UserRepository userRepository,
            DogRepository dogRepository,
            DogService dogService,
            TrainingSessionService trainingSessionService,
            FollowService followService,
            ModerationService moderationService,
            CurrentUserProvider currentUserProvider,
            StorageService storageService
    ) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.dogRepository = dogRepository;
        this.dogService = dogService;
        this.trainingSessionService = trainingSessionService;
        this.followService = followService;
        this.moderationService = moderationService;
        this.currentUserProvider = currentUserProvider;
        this.storageService = storageService;
    }

    @Transactional
    public PostResponse createPost(CreatePostRequest request) {
        UUID authorId = currentUserProvider.getCurrentUserId();
        if (request.dogId() != null) {
            dogService.getOwnedDog(request.dogId());
        }
        Post post = new Post(UUID.randomUUID(), authorId, request.dogId(), null, request.content(), null, Instant.now());
        postRepository.save(post);
        return toResponse(post);
    }

    @Transactional
    public PostResponse createPostFromSession(UUID sessionId, CreatePostFromSessionRequest request) {
        // getSession() enforces that the session's dog belongs to the current user.
        TrainingSessionResponse session = trainingSessionService.getSession(sessionId);
        if (session.status() != SessionStatus.COMPLETED) {
            throw new BusinessRuleException("Only completed training sessions can be shared as a post");
        }
        if (postRepository.existsByTrainingSessionId(sessionId)) {
            throw new BusinessRuleException("This training session has already been posted");
        }

        UUID authorId = currentUserProvider.getCurrentUserId();
        Dog dog = dogService.getOwnedDog(session.dogId());
        String content = request.content() != null && !request.content().isBlank()
                ? request.content()
                : buildSessionCaption(dog, session);

        Post post = new Post(UUID.randomUUID(), authorId, session.dogId(), sessionId, content, null, Instant.now());
        postRepository.save(post);
        return toResponse(post);
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(UUID postId) {
        return toResponse(getPostOrThrow(postId));
    }

    @Transactional
    public void deletePost(UUID postId) {
        Post post = getOwnedPost(postId);
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(post.getImageUrl()));
        postRepository.delete(post);
    }

    public UploadUrlResponse createMediaUploadUrl(UUID postId, UploadUrlRequest request) {
        getOwnedPost(postId);
        MediaCategory category = MediaCategory.fromContentType(request.contentType())
                .filter(c -> c == MediaCategory.IMAGE)
                .orElseThrow(() -> new InvalidFileException("Unsupported content type: " + request.contentType()));
        return storageService.createUploadUrl(mediaKeyPrefix(postId), category, request);
    }

    @Transactional
    public PostResponse confirmMedia(UUID postId, PostMediaConfirmRequest request) {
        Post post = getOwnedPost(postId);
        String objectKey = request.objectKey();
        if (!objectKey.startsWith(mediaKeyPrefix(postId) + "/")) {
            throw new AccessDeniedForResourceException("You do not have access to this object");
        }
        if (!storageService.objectExists(objectKey)) {
            throw ResourceNotFoundException.forEntity("Object", objectKey);
        }
        String resizedKey = storageService.resizeStoredImage(objectKey, ImageProcessingService.POST_MAX_DIMENSION);
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(post.getImageUrl()));
        post.setImageUrl(storageService.buildPublicUrl(resizedKey));
        return toResponse(post);
    }

    @Transactional
    public void deleteMedia(UUID postId) {
        Post post = getOwnedPost(postId);
        storageService.deleteObjectIfPresent(storageService.extractObjectKey(post.getImageUrl()));
        post.setImageUrl(null);
    }

    @Transactional(readOnly = true)
    public PostPageResponse listUserPosts(UUID userId, String cursor, Integer limit) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        if (!currentUserId.equals(userId) && moderationService.isBlockedEitherWay(currentUserId, userId)) {
            // Hide silently rather than 403 - a block should look like the user has no posts.
            return new PostPageResponse(List.of(), null);
        }
        int pageSize = pageSize(limit);
        List<Post> posts = postRepository.findPage(List.of(userId), cursorCreatedAt(cursor), cursorId(cursor), PageRequest.of(0, pageSize + 1));
        return toPage(posts, pageSize);
    }

    @Transactional(readOnly = true)
    public PostPageResponse getFeed(String cursor, Integer limit) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        List<UUID> blockedRelatedIds = moderationService.getRelatedBlockedUserIds(currentUserId);
        List<UUID> authorIds = new ArrayList<>(followService.getFollowingIds(currentUserId));
        authorIds.removeAll(blockedRelatedIds);
        authorIds.add(currentUserId);
        int pageSize = pageSize(limit);
        List<Post> posts = postRepository.findPage(authorIds, cursorCreatedAt(cursor), cursorId(cursor), PageRequest.of(0, pageSize + 1));
        return toPage(posts, pageSize);
    }

    private String buildSessionCaption(Dog dog, TrainingSessionResponse session) {
        int exerciseCount = session.exercises().size();
        String exercisePart = exerciseCount == 1 ? "1 exercise" : exerciseCount + " exercises";
        return "%s finished a %d-minute training session with %s!"
                .formatted(dog.getName(), session.durationMinutes(), exercisePart);
    }

    /**
     * {@code fetched} may contain one extra row beyond {@code pageSize} (the caller over-fetches
     * by one) purely to detect whether another page exists, without a separate count query.
     */
    private PostPageResponse toPage(List<Post> fetched, int pageSize) {
        boolean hasMore = fetched.size() > pageSize;
        List<Post> posts = hasMore ? fetched.subList(0, pageSize) : fetched;
        List<PostResponse> items = enrich(posts);
        String nextCursor = hasMore ? PostCursor.encode(posts.getLast().getCreatedAt(), posts.getLast().getId()) : null;
        return new PostPageResponse(items, nextCursor);
    }

    private List<PostResponse> enrich(List<Post> posts) {
        List<UUID> authorIds = posts.stream().map(Post::getAuthorId).distinct().toList();
        List<UUID> dogIds = posts.stream().map(Post::getDogId).filter(Objects::nonNull).distinct().toList();

        Map<UUID, User> authorsById = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(user -> authorsById.put(user.getId(), user));

        Map<UUID, Dog> dogsById = new HashMap<>();
        dogRepository.findAllById(dogIds).forEach(dog -> dogsById.put(dog.getId(), dog));

        return posts.stream().map(post -> toResponse(post, authorsById, dogsById)).toList();
    }

    private PostResponse toResponse(Post post) {
        Map<UUID, User> authorsById = new HashMap<>();
        userRepository.findById(post.getAuthorId()).ifPresent(user -> authorsById.put(user.getId(), user));
        Map<UUID, Dog> dogsById = new HashMap<>();
        if (post.getDogId() != null) {
            dogRepository.findById(post.getDogId()).ifPresent(dog -> dogsById.put(dog.getId(), dog));
        }
        return toResponse(post, authorsById, dogsById);
    }

    private PostResponse toResponse(Post post, Map<UUID, User> authorsById, Map<UUID, Dog> dogsById) {
        User author = authorsById.get(post.getAuthorId());
        Dog dog = post.getDogId() != null ? dogsById.get(post.getDogId()) : null;
        return new PostResponse(
                post.getId(),
                post.getAuthorId(),
                author != null ? author.getName() : null,
                author != null ? author.getAvatarUrl() : null,
                post.getDogId(),
                dog != null ? dog.getName() : null,
                post.getTrainingSessionId(),
                post.getContent(),
                post.getImageUrl(),
                post.getCreatedAt()
        );
    }

    private Post getPostOrThrow(UUID postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("Post", postId));
    }

    /**
     * Fetches a post and enforces that the currently authenticated user is its author. Unlike
     * {@code getOwnedDog}/{@code getOwnedSession}, reading a post ({@link #getPost}) is not
     * ownership-scoped - posts are visible feed-wide - only mutating one is.
     */
    private Post getOwnedPost(UUID postId) {
        Post post = getPostOrThrow(postId);
        if (!post.getAuthorId().equals(currentUserProvider.getCurrentUserId())) {
            throw new AccessDeniedForResourceException("You do not have access to this post");
        }
        return post;
    }

    private String mediaKeyPrefix(UUID postId) {
        return "posts/" + postId;
    }

    private int pageSize(Integer limit) {
        return limit == null ? DEFAULT_PAGE_LIMIT : Math.clamp(limit, 1, MAX_PAGE_LIMIT);
    }

    private Instant cursorCreatedAt(String cursor) {
        return cursor == null ? null : PostCursor.decode(cursor).createdAt();
    }

    private UUID cursorId(String cursor) {
        return cursor == null ? null : PostCursor.decode(cursor).id();
    }
}
