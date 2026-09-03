package com.oskott.dogtrainerbackend.stats.service;

import com.oskott.dogtrainerbackend.dog.service.DogService;
import com.oskott.dogtrainerbackend.stats.dto.DogProgressResponse;
import com.oskott.dogtrainerbackend.stats.dto.DogStatisticsResponse;
import com.oskott.dogtrainerbackend.stats.dto.ExerciseProgressEntry;
import com.oskott.dogtrainerbackend.stats.dto.ExerciseProgressPoint;
import com.oskott.dogtrainerbackend.training.dto.SessionExerciseResponse;
import com.oskott.dogtrainerbackend.training.dto.TrainingSessionResponse;
import com.oskott.dogtrainerbackend.training.entity.Exercise;
import com.oskott.dogtrainerbackend.training.entity.SessionExercise;
import com.oskott.dogtrainerbackend.training.entity.SessionStatus;
import com.oskott.dogtrainerbackend.training.entity.TrainingSession;
import com.oskott.dogtrainerbackend.training.repository.ExerciseRepository;
import com.oskott.dogtrainerbackend.training.repository.SessionExerciseRepository;
import com.oskott.dogtrainerbackend.training.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes read-only training statistics and progress data for a dog by aggregating its
 * training sessions and session exercises. Intentionally loops per-session (rather than
 * adding cross-entity JPQL joins) since session volume per dog is small for this app.
 */
@Service
@Transactional(readOnly = true)
public class DogStatisticsService {

    private static final int PROGRESS_WEEKS_WINDOW = 8;

    private final DogService dogService;
    private final TrainingSessionRepository trainingSessionRepository;
    private final SessionExerciseRepository sessionExerciseRepository;
    private final ExerciseRepository exerciseRepository;

    public DogStatisticsService(
            DogService dogService,
            TrainingSessionRepository trainingSessionRepository,
            SessionExerciseRepository sessionExerciseRepository,
            ExerciseRepository exerciseRepository
    ) {
        this.dogService = dogService;
        this.trainingSessionRepository = trainingSessionRepository;
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.exerciseRepository = exerciseRepository;
    }

    public DogStatisticsResponse getStatistics(UUID dogId) {
        dogService.getOwnedDog(dogId);
        List<TrainingSession> sessions = trainingSessionRepository.findAllByDogIdOrderByStartedAtDesc(dogId);
        List<TrainingSession> completed = sessions.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).toList();

        long totalTrainingMinutes = completed.stream()
                .mapToLong(s -> s.getDurationMinutes() != null ? s.getDurationMinutes() : 0)
                .sum();

        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long sessionsThisWeek = completed.stream().filter(s -> s.getStartedAt().isAfter(weekAgo)).count();

        List<SessionExercise> allExercises = completed.stream()
                .flatMap(s -> sessionExerciseRepository.findAllBySessionIdOrderByIdAsc(s.getId()).stream())
                .toList();
        double averageSuccessRate = weightedSuccessRate(allExercises);

        int currentStreakDays = computeStreakDays(completed);

        return new DogStatisticsResponse(
                sessions.size(),
                completed.size(),
                totalTrainingMinutes,
                sessionsThisWeek,
                currentStreakDays,
                averageSuccessRate
        );
    }

    public DogProgressResponse getProgress(UUID dogId) {
        dogService.getOwnedDog(dogId);
        List<TrainingSession> sessions = trainingSessionRepository.findAllByDogIdOrderByStartedAtDesc(dogId);
        List<TrainingSession> completed = sessions.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).toList();

        Map<UUID, List<SessionExercise>> exercisesBySession = new LinkedHashMap<>();
        for (TrainingSession session : sessions) {
            exercisesBySession.put(session.getId(), sessionExerciseRepository.findAllBySessionIdOrderByIdAsc(session.getId()));
        }

        List<TrainingSessionResponse> history = sessions.stream()
                .map(session -> TrainingSessionResponse.from(
                        session,
                        exercisesBySession.get(session.getId()).stream().map(SessionExerciseResponse::from).toList()
                ))
                .toList();

        long totalTrainingMinutes = completed.stream()
                .mapToLong(s -> s.getDurationMinutes() != null ? s.getDurationMinutes() : 0)
                .sum();

        Instant windowStart = Instant.now().minus((long) PROGRESS_WEEKS_WINDOW * 7, ChronoUnit.DAYS);
        long sessionsInWindow = completed.stream().filter(s -> s.getStartedAt().isAfter(windowStart)).count();
        double sessionsPerWeek = (double) sessionsInWindow / PROGRESS_WEEKS_WINDOW;

        List<SessionExercise> allExercises = completed.stream()
                .flatMap(s -> exercisesBySession.get(s.getId()).stream())
                .toList();
        double averageSuccessRate = weightedSuccessRate(allExercises);

        int currentStreakDays = computeStreakDays(completed);

        List<ExerciseProgressEntry> exerciseProgress = buildExerciseProgress(completed, exercisesBySession);

        return new DogProgressResponse(
                history,
                totalTrainingMinutes,
                sessionsPerWeek,
                currentStreakDays,
                averageSuccessRate,
                exerciseProgress
        );
    }

    private List<ExerciseProgressEntry> buildExerciseProgress(
            List<TrainingSession> completedSessionsDesc,
            Map<UUID, List<SessionExercise>> exercisesBySession
    ) {
        // Walk sessions oldest-first so each exercise's points are chronologically ordered.
        List<TrainingSession> completedSessionsAsc = completedSessionsDesc.stream()
                .sorted(Comparator.comparing(TrainingSession::getStartedAt))
                .toList();

        Map<UUID, List<ExerciseProgressPoint>> pointsByExercise = new LinkedHashMap<>();
        for (TrainingSession session : completedSessionsAsc) {
            for (SessionExercise sessionExercise : exercisesBySession.get(session.getId())) {
                pointsByExercise
                        .computeIfAbsent(sessionExercise.getExerciseId(), key -> new ArrayList<>())
                        .add(new ExerciseProgressPoint(
                                session.getStartedAt(),
                                sessionExercise.getRepetitions(),
                                sessionExercise.getSuccessfulRepetitions(),
                                sessionExercise.successRate()
                        ));
            }
        }

        if (pointsByExercise.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> exerciseNames = exerciseRepository.findAllById(pointsByExercise.keySet()).stream()
                .collect(Collectors.toMap(Exercise::getId, Exercise::getName));

        return pointsByExercise.entrySet().stream()
                .map(entry -> new ExerciseProgressEntry(
                        entry.getKey(),
                        exerciseNames.getOrDefault(entry.getKey(), "Exercise"),
                        entry.getValue()
                ))
                .toList();
    }

    private double weightedSuccessRate(List<SessionExercise> exercises) {
        long totalReps = exercises.stream().mapToLong(SessionExercise::getRepetitions).sum();
        if (totalReps == 0) {
            return 0.0;
        }
        long totalSuccessful = exercises.stream().mapToLong(SessionExercise::getSuccessfulRepetitions).sum();
        return (double) totalSuccessful / totalReps;
    }

    /**
     * Counts consecutive days (ending today or yesterday) that had at least one completed
     * training session. Training yesterday but not yet today still counts as an active streak.
     */
    private int computeStreakDays(List<TrainingSession> completedSessions) {
        Set<LocalDate> trainedDates = completedSessions.stream()
                .map(s -> s.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate())
                .collect(Collectors.toSet());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate cursor = trainedDates.contains(today) ? today : today.minusDays(1);

        int streak = 0;
        while (trainedDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
