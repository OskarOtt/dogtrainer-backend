package com.oskott.dogtrainerbackend.activity.service;

import com.oskott.dogtrainerbackend.activity.dto.CreatePhysicalActivityRequest;
import com.oskott.dogtrainerbackend.activity.dto.PhysicalActivityResponse;
import com.oskott.dogtrainerbackend.activity.dto.UpdatePhysicalActivityRequest;
import com.oskott.dogtrainerbackend.activity.entity.ActivityStatus;
import com.oskott.dogtrainerbackend.activity.entity.ActivityType;
import com.oskott.dogtrainerbackend.activity.entity.PhysicalActivity;
import com.oskott.dogtrainerbackend.activity.repository.PhysicalActivityRepository;
import com.oskott.dogtrainerbackend.common.exception.BusinessRuleException;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.common.i18n.SupportedLocale;
import com.oskott.dogtrainerbackend.dog.service.DogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PhysicalActivityService {

    private final PhysicalActivityRepository physicalActivityRepository;
    private final DogService dogService;

    public PhysicalActivityService(PhysicalActivityRepository physicalActivityRepository, DogService dogService) {
        this.physicalActivityRepository = physicalActivityRepository;
        this.dogService = dogService;
    }

    @Transactional(readOnly = true)
    public List<PhysicalActivityResponse> listActivitiesForDog(UUID dogId) {
        dogService.getOwnedDog(dogId);
        return physicalActivityRepository.findAllByDogIdOrderByStartedAtDesc(dogId).stream()
                .map(PhysicalActivityResponse::from)
                .toList();
    }

    @Transactional
    public PhysicalActivityResponse createActivity(UUID dogId, CreatePhysicalActivityRequest request) {
        dogService.getOwnedDog(dogId);
        String title = request.title() != null && !request.title().isBlank()
                ? request.title()
                : defaultTitle(request.activityType());
        PhysicalActivity activity = new PhysicalActivity(
                UUID.randomUUID(),
                dogId,
                request.activityType(),
                title,
                Instant.now(),
                ActivityStatus.IN_PROGRESS
        );
        physicalActivityRepository.save(activity);
        return PhysicalActivityResponse.from(activity);
    }

    @Transactional(readOnly = true)
    public PhysicalActivityResponse getActivity(UUID activityId) {
        return PhysicalActivityResponse.from(getOwnedActivity(activityId));
    }

    /**
     * Plain, ownership-unchecked lookup for use by other features (e.g. {@code PostService}) that
     * have already established the caller is allowed to see this specific activity through some
     * other relationship (such as a publicly readable post it was shared from). Callers must not
     * expose this as a generic "fetch any activity by id" path.
     */
    @Transactional(readOnly = true)
    public PhysicalActivityResponse getActivityById(UUID activityId) {
        return PhysicalActivityResponse.from(getActivityOrThrow(activityId));
    }

    @Transactional
    public PhysicalActivityResponse updateActivity(UUID activityId, UpdatePhysicalActivityRequest request) {
        PhysicalActivity activity = getOwnedActivity(activityId);
        activity.setTitle(request.title());
        activity.setNotes(request.notes());
        return PhysicalActivityResponse.from(activity);
    }

    @Transactional
    public PhysicalActivityResponse pauseActivity(UUID activityId) {
        PhysicalActivity activity = getOwnedActivity(activityId);
        if (activity.getStatus() != ActivityStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Only an in-progress activity can be paused");
        }
        activity.setStatus(ActivityStatus.PAUSED);
        activity.setPausedAt(Instant.now());
        return PhysicalActivityResponse.from(activity);
    }

    @Transactional
    public PhysicalActivityResponse resumeActivity(UUID activityId) {
        PhysicalActivity activity = getOwnedActivity(activityId);
        if (activity.getStatus() != ActivityStatus.PAUSED) {
            throw new BusinessRuleException("Only a paused activity can be resumed");
        }
        Instant pausedAt = activity.getPausedAt();
        if (pausedAt != null) {
            activity.setTotalPausedSeconds(activity.getTotalPausedSeconds() + Duration.between(pausedAt, Instant.now()).getSeconds());
        }
        activity.setPausedAt(null);
        activity.setStatus(ActivityStatus.IN_PROGRESS);
        return PhysicalActivityResponse.from(activity);
    }

    @Transactional
    public PhysicalActivityResponse completeActivity(UUID activityId) {
        PhysicalActivity activity = getOwnedActivity(activityId);
        if (activity.getStatus() != ActivityStatus.IN_PROGRESS && activity.getStatus() != ActivityStatus.PAUSED) {
            throw new BusinessRuleException("Only an in-progress or paused activity can be completed");
        }
        Instant now = Instant.now();
        // If completed while paused, the time since pausing shouldn't count towards the duration.
        long trailingPauseSeconds = activity.getStatus() == ActivityStatus.PAUSED && activity.getPausedAt() != null
                ? Duration.between(activity.getPausedAt(), now).getSeconds()
                : 0;
        long totalPausedSeconds = activity.getTotalPausedSeconds() + trailingPauseSeconds;
        long elapsedSeconds = Duration.between(activity.getStartedAt(), now).getSeconds() - totalPausedSeconds;
        activity.setStatus(ActivityStatus.COMPLETED);
        activity.setPausedAt(null);
        activity.setTotalPausedSeconds(totalPausedSeconds);
        activity.setCompletedAt(now);
        activity.setDurationMinutes((int) Math.max(1, elapsedSeconds / 60));
        return PhysicalActivityResponse.from(activity);
    }

    @Transactional
    public PhysicalActivityResponse cancelActivity(UUID activityId) {
        PhysicalActivity activity = getOwnedActivity(activityId);
        if (activity.getStatus() != ActivityStatus.IN_PROGRESS && activity.getStatus() != ActivityStatus.PAUSED) {
            throw new BusinessRuleException("Only an in-progress or paused activity can be cancelled");
        }
        activity.setStatus(ActivityStatus.CANCELLED);
        activity.setCompletedAt(Instant.now());
        return PhysicalActivityResponse.from(activity);
    }

    private String defaultTitle(ActivityType activityType) {
        boolean bokmal = SupportedLocale.isBokmal();
        return switch (activityType) {
            case WALK -> bokmal ? "Gåtur" : "Walk";
            case RUN -> bokmal ? "Løpetur" : "Run";
            case SKI -> bokmal ? "Skitur" : "Ski";
            case STRENGTH_TRAINING -> bokmal ? "Styrketrening" : "Strength training";
            case SWIM -> bokmal ? "Svømming" : "Swim";
            case HIKE -> bokmal ? "Fottur" : "Hike";
            case PLAY_SESSION -> bokmal ? "Lekestund" : "Play session";
        };
    }

    /**
     * Fetches a physical activity and enforces that its dog belongs to the currently
     * authenticated user, so no user can ever read or modify another user's activities.
     */
    private PhysicalActivity getOwnedActivity(UUID activityId) {
        PhysicalActivity activity = getActivityOrThrow(activityId);
        // Throws AccessDeniedForResourceException if the dog isn't owned by the current user.
        dogService.getOwnedDog(activity.getDogId());
        return activity;
    }

    private PhysicalActivity getActivityOrThrow(UUID activityId) {
        return physicalActivityRepository.findById(activityId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("PhysicalActivity", activityId));
    }
}
