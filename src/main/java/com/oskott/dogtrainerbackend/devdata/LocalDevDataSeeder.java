package com.oskott.dogtrainerbackend.devdata;

import com.oskott.dogtrainerbackend.dog.entity.Dog;
import com.oskott.dogtrainerbackend.dog.entity.Sex;
import com.oskott.dogtrainerbackend.dog.repository.DogRepository;
import com.oskott.dogtrainerbackend.post.entity.Post;
import com.oskott.dogtrainerbackend.post.repository.PostRepository;
import com.oskott.dogtrainerbackend.training.entity.SessionStatus;
import com.oskott.dogtrainerbackend.training.entity.TrainingSession;
import com.oskott.dogtrainerbackend.training.repository.TrainingSessionRepository;
import com.oskott.dogtrainerbackend.user.entity.User;
import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Seeds a couple of demo users, a dog, a completed training session and a post about it, so the
 * local (H2, in-memory) profile has something to look at without manual setup. Never runs
 * outside {@code local} - test/prod profiles are unaffected.
 */
@Component
@Profile("local")
public class LocalDevDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DogRepository dogRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final PostRepository postRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalDevDataSeeder(
            UserRepository userRepository,
            DogRepository dogRepository,
            TrainingSessionRepository trainingSessionRepository,
            PostRepository postRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.dogRepository = dogRepository;
        this.trainingSessionRepository = trainingSessionRepository;
        this.postRepository = postRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        Instant now = Instant.now();

        User owner = new User(
                UUID.randomUUID(),
                "demo1@dogtrainer.local",
                "Demo One",
                passwordEncoder.encode("password123"),
                null,
                now
        );
        userRepository.save(owner);

        User other = new User(
                UUID.randomUUID(),
                "demo2@dogtrainer.local",
                "Demo Two",
                passwordEncoder.encode("password123"),
                null,
                now
        );
        userRepository.save(other);

        Dog dog = new Dog(
                UUID.randomUUID(),
                owner.getId(),
                "Buddy",
                "Golden Retriever",
                java.time.LocalDate.of(2021, 5, 12),
                Sex.MALE,
                BigDecimal.valueOf(28.5),
                null,
                null,
                0,
                now
        );
        dogRepository.save(dog);

        TrainingSession session = new TrainingSession(
                UUID.randomUUID(),
                dog.getId(),
                now.minus(1, ChronoUnit.HOURS),
                "Backyard",
                "Worked on sit, stay and recall.",
                SessionStatus.COMPLETED
        );
        session.setCompletedAt(now.minus(30, ChronoUnit.MINUTES));
        session.setDurationMinutes(30);
        trainingSessionRepository.save(session);

        Post post = new Post(
                UUID.randomUUID(),
                owner.getId(),
                dog.getId(),
                session.getId(),
                "Great training session with Buddy today - sit, stay and recall are looking solid!",
                null,
                now
        );
        postRepository.save(post);
    }
}
