package com.oskott.dogtrainerbackend.post.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_dogs", joinColumns = @JoinColumn(name = "post_id"))
    @OrderColumn(name = "list_index")
    @Column(name = "dog_id", nullable = false)
    private List<UUID> dogIds = new ArrayList<>();

    @Column(name = "training_session_id")
    private UUID trainingSessionId;

    @Column(name = "physical_activity_id")
    private UUID physicalActivityId;

    @Column(nullable = false)
    private String content;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Post() {
        // JPA
    }

    public Post(UUID id, UUID authorId, List<UUID> dogIds, UUID trainingSessionId, String content, String imageUrl, Instant createdAt) {
        this(id, authorId, dogIds, trainingSessionId, null, content, imageUrl, createdAt);
    }

    public Post(
            UUID id,
            UUID authorId,
            List<UUID> dogIds,
            UUID trainingSessionId,
            UUID physicalActivityId,
            String content,
            String imageUrl,
            Instant createdAt
    ) {
        this.id = id;
        this.authorId = authorId;
        this.dogIds = new ArrayList<>(dogIds);
        this.trainingSessionId = trainingSessionId;
        this.physicalActivityId = physicalActivityId;
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

    public List<UUID> getDogIds() {
        return dogIds;
    }

    public UUID getTrainingSessionId() {
        return trainingSessionId;
    }

    public UUID getPhysicalActivityId() {
        return physicalActivityId;
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
