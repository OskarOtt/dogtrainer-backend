package com.oskott.dogtrainerbackend.training.service;

import com.oskott.dogtrainerbackend.common.exception.BusinessRuleException;
import com.oskott.dogtrainerbackend.common.exception.ResourceNotFoundException;
import com.oskott.dogtrainerbackend.dog.service.DogService;
import com.oskott.dogtrainerbackend.training.dto.AddSessionExerciseRequest;
import com.oskott.dogtrainerbackend.training.dto.CreateTrainingSessionRequest;
import com.oskott.dogtrainerbackend.training.dto.SessionExerciseResponse;
import com.oskott.dogtrainerbackend.training.dto.TrainingSessionResponse;
import com.oskott.dogtrainerbackend.training.dto.UpdateSessionExerciseRequest;
import com.oskott.dogtrainerbackend.training.dto.UpdateTrainingSessionRequest;
import com.oskott.dogtrainerbackend.training.entity.SessionExercise;
import com.oskott.dogtrainerbackend.training.entity.SessionStatus;
import com.oskott.dogtrainerbackend.training.entity.TrainingSession;
import com.oskott.dogtrainerbackend.training.repository.ExerciseRepository;
import com.oskott.dogtrainerbackend.training.repository.SessionExerciseRepository;
import com.oskott.dogtrainerbackend.training.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TrainingSessionService {

    private final TrainingSessionRepository trainingSessionRepository;
    private final SessionExerciseRepository sessionExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final DogService dogService;

    public TrainingSessionService(
            TrainingSessionRepository trainingSessionRepository,
            SessionExerciseRepository sessionExerciseRepository,
            ExerciseRepository exerciseRepository,
            DogService dogService
    ) {
        this.trainingSessionRepository = trainingSessionRepository;
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.dogService = dogService;
    }

    @Transactional(readOnly = true)
    public List<TrainingSessionResponse> listSessionsForDog(UUID dogId) {
        dogService.getOwnedDog(dogId);
        return trainingSessionRepository.findAllByDogIdOrderByStartedAtDesc(dogId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TrainingSessionResponse createSession(UUID dogId, CreateTrainingSessionRequest request) {
        dogService.getOwnedDog(dogId);
        TrainingSession session = new TrainingSession(
                UUID.randomUUID(),
                dogId,
                Instant.now(),
                request.location(),
                request.notes(),
                SessionStatus.IN_PROGRESS
        );
        trainingSessionRepository.save(session);
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public TrainingSessionResponse getSession(UUID sessionId) {
        return toResponse(getOwnedSession(sessionId));
    }

    @Transactional
    public TrainingSessionResponse updateSession(UUID sessionId, UpdateTrainingSessionRequest request) {
        TrainingSession session = getOwnedSession(sessionId);
        session.setLocation(request.location());
        session.setNotes(request.notes());
        return toResponse(session);
    }

    @Transactional
    public TrainingSessionResponse completeSession(UUID sessionId) {
        TrainingSession session = getOwnedSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Only an in-progress session can be completed");
        }
        Instant now = Instant.now();
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(now);
        session.setDurationMinutes((int) Math.max(1, Duration.between(session.getStartedAt(), now).toMinutes()));
        return toResponse(session);
    }

    @Transactional
    public TrainingSessionResponse cancelSession(UUID sessionId) {
        TrainingSession session = getOwnedSession(sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Only an in-progress session can be cancelled");
        }
        session.setStatus(SessionStatus.CANCELLED);
        session.setCompletedAt(Instant.now());
        return toResponse(session);
    }

    @Transactional
    public TrainingSessionResponse addSessionExercise(UUID sessionId, AddSessionExerciseRequest request) {
        TrainingSession session = getOwnedSession(sessionId);
        requireInProgress(session);
        if (!exerciseRepository.existsById(request.exerciseId())) {
            throw ResourceNotFoundException.forEntity("Exercise", request.exerciseId());
        }
        int repetitions = request.repetitions() != null ? request.repetitions() : 0;
        int successfulRepetitions = request.successfulRepetitions() != null ? request.successfulRepetitions() : 0;
        requireValidRepetitionCounts(repetitions, successfulRepetitions);

        SessionExercise sessionExercise = new SessionExercise(
                UUID.randomUUID(),
                sessionId,
                request.exerciseId(),
                repetitions,
                successfulRepetitions,
                request.difficulty(),
                request.notes()
        );
        sessionExerciseRepository.save(sessionExercise);
        return toResponse(session);
    }

    @Transactional
    public TrainingSessionResponse updateSessionExercise(UUID sessionId, UUID sessionExerciseId, UpdateSessionExerciseRequest request) {
        TrainingSession session = getOwnedSession(sessionId);
        SessionExercise sessionExercise = getOwnedSessionExercise(sessionId, sessionExerciseId);

        int repetitions = request.repetitions() != null ? request.repetitions() : sessionExercise.getRepetitions();
        int successfulRepetitions = request.successfulRepetitions() != null
                ? request.successfulRepetitions()
                : sessionExercise.getSuccessfulRepetitions();
        requireValidRepetitionCounts(repetitions, successfulRepetitions);

        sessionExercise.setRepetitions(repetitions);
        sessionExercise.setSuccessfulRepetitions(successfulRepetitions);
        if (request.difficulty() != null) {
            sessionExercise.setDifficulty(request.difficulty());
        }
        if (request.notes() != null) {
            sessionExercise.setNotes(request.notes());
        }
        return toResponse(session);
    }

    @Transactional
    public TrainingSessionResponse removeSessionExercise(UUID sessionId, UUID sessionExerciseId) {
        TrainingSession session = getOwnedSession(sessionId);
        getOwnedSessionExercise(sessionId, sessionExerciseId);
        sessionExerciseRepository.deleteBySessionIdAndId(sessionId, sessionExerciseId);
        return toResponse(session);
    }

    private void requireInProgress(TrainingSession session) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Exercises can only be modified while the session is in progress");
        }
    }

    private void requireValidRepetitionCounts(int repetitions, int successfulRepetitions) {
        if (successfulRepetitions > repetitions) {
            throw new BusinessRuleException("Successful repetitions cannot exceed total repetitions");
        }
    }

    /**
     * Fetches a training session and enforces that its dog belongs to the currently
     * authenticated user, so no user can ever read or modify another user's sessions.
     */
    private TrainingSession getOwnedSession(UUID sessionId) {
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("TrainingSession", sessionId));
        // Throws AccessDeniedForResourceException if the dog isn't owned by the current user.
        dogService.getOwnedDog(session.getDogId());
        return session;
    }

    private SessionExercise getOwnedSessionExercise(UUID sessionId, UUID sessionExerciseId) {
        SessionExercise sessionExercise = sessionExerciseRepository.findById(sessionExerciseId)
                .orElseThrow(() -> ResourceNotFoundException.forEntity("SessionExercise", sessionExerciseId));
        if (!sessionExercise.getSessionId().equals(sessionId)) {
            throw new ResourceNotFoundException("SessionExercise not found with id: " + sessionExerciseId);
        }
        return sessionExercise;
    }

    private TrainingSessionResponse toResponse(TrainingSession session) {
        List<SessionExerciseResponse> exercises = sessionExerciseRepository.findAllBySessionIdOrderByIdAsc(session.getId()).stream()
                .map(SessionExerciseResponse::from)
                .toList();
        return TrainingSessionResponse.from(session, exercises);
    }
}
