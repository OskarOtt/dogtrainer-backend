package com.oskott.dogtrainerbackend.post.controller;

import com.oskott.dogtrainerbackend.post.dto.CreatePostFromSessionRequest;
import com.oskott.dogtrainerbackend.post.dto.CreatePostRequest;
import com.oskott.dogtrainerbackend.post.dto.PostMediaConfirmRequest;
import com.oskott.dogtrainerbackend.post.dto.PostPageResponse;
import com.oskott.dogtrainerbackend.post.dto.PostResponse;
import com.oskott.dogtrainerbackend.post.service.PostService;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlRequest;
import com.oskott.dogtrainerbackend.storage.dto.UploadUrlResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(request));
    }

    @PostMapping("/posts/from-session/{sessionId}")
    public ResponseEntity<PostResponse> createPostFromSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CreatePostFromSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPostFromSession(sessionId, request));
    }

    @GetMapping("/posts/{id}")
    public PostResponse getPost(@PathVariable UUID id) {
        return postService.getPost(id);
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        postService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/media/upload-url")
    public UploadUrlResponse createMediaUploadUrl(@PathVariable UUID id, @Valid @RequestBody UploadUrlRequest request) {
        return postService.createMediaUploadUrl(id, request);
    }

    @PutMapping("/posts/{id}/media")
    public PostResponse confirmMedia(@PathVariable UUID id, @Valid @RequestBody PostMediaConfirmRequest request) {
        return postService.confirmMedia(id, request);
    }

    @DeleteMapping("/posts/{id}/media")
    public ResponseEntity<Void> deleteMedia(@PathVariable UUID id) {
        postService.deleteMedia(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/posts")
    public PostPageResponse listUserPosts(
            @PathVariable UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return postService.listUserPosts(userId, cursor, limit);
    }

    @GetMapping("/feed")
    public PostPageResponse getFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return postService.getFeed(cursor, limit);
    }
}
