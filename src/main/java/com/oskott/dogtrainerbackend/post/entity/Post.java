package com.oskott.dogtrainerbackend.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "dog_id")
    private UUID dogId;

    @Column(name = "training_session_id")
    private UUID trainingSessionId;

    @Column(nullable = false)
    private String content;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Post() {
        // JPA
    }

    public Post(UUID id, UUID authorId, UUID dogId, UUID trainingSessionId, String content, String imageUrl, Instant createdAt) {
        this.id = id;
        this.authorId = authorId;
        this.dogId = dogId;
        this.trainingSessionId = trainingSessionId;
        this.content = content;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public UUID getDogId() {
        return dogId;
    }

    public UUID getTrainingSessionId() {
        return trainingSessionId;
    }

    public String getContent() {
        return content;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
