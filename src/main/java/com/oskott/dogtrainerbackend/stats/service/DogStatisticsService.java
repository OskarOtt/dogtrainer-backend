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
import com.oskott.dogtrainerbackend.training.service.TrainingCatalogLocalizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
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
    private final TrainingCatalogLocalizationService catalogLocalizationService;

    public DogStatisticsService(
            DogService dogService,
            TrainingSessionRepository trainingSessionRepository,
            SessionExerciseRepository sessionExerciseRepository,
            ExerciseRepository exerciseRepository,
            TrainingCatalogLocalizationService catalogLocalizationService
    ) {
        this.dogService = dogService;
        this.trainingSessionRepository = trainingSessionRepository;
        this.sessionExerciseRepository = sessionExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.catalogLocalizationService = catalogLocalizationService;
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

        int currentStreakWeeks = computeStreakWeeks(completed);

        return new DogStatisticsResponse(
                sessions.size(),
                completed.size(),
                totalTrainingMinutes,
                sessionsThisWeek,
                currentStreakWeeks,
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

        int currentStreakWeeks = computeStreakWeeks(completed);

        List<ExerciseProgressEntry> exerciseProgress = buildExerciseProgress(completed, exercisesBySession);

        return new DogProgressResponse(
                history,
                totalTrainingMinutes,
                sessionsPerWeek,
                currentStreakWeeks,
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

        List<Exercise> exercises = exerciseRepository.findAllById(pointsByExercise.keySet());
        Map<UUID, String> exerciseNames = catalogLocalizationService.localizedExerciseNames(exercises);

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
     * Counts consecutive weeks (ending this week or last week) that had at least one completed
     * training session. Weeks follow the ISO week-based year/week-of-year definition, so a week
     * runs Monday-Sunday. Training last week but not yet this week still counts as an active streak,
     * since dog training isn't expected every single day.
     */
    private int computeStreakWeeks(List<TrainingSession> completedSessions) {
        Set<WeekKey> trainedWeeks = completedSessions.stream()
                .map(s -> weekKeyOf(s.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate()))
                .collect(Collectors.toSet());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        WeekKey currentWeek = weekKeyOf(today);
        LocalDate cursor = trainedWeeks.contains(currentWeek) ? today : today.minusWeeks(1);

        int streak = 0;
        WeekKey cursorWeek = weekKeyOf(cursor);
        while (trainedWeeks.contains(cursorWeek)) {
            streak++;
            cursor = cursor.minusWeeks(1);
            cursorWeek = weekKeyOf(cursor);
        }
        return streak;
    }

    private WeekKey weekKeyOf(LocalDate date) {
        return new WeekKey(
                date.get(IsoFields.WEEK_BASED_YEAR),
                date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        );
    }

    private record WeekKey(int weekBasedYear, int week) {
    }
}
