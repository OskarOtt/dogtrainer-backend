package com.oskott.dogtrainerbackend.user.service;

import com.oskott.dogtrainerbackend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsernameGeneratorTest {

    @Test
    void slugifiesLowercasesAndStripsNonAlphanumerics() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        UsernameGenerator generator = new UsernameGenerator(userRepository);

        assertThat(generator.generate("Anna Lee, Jr.")).isEqualTo("anna-lee-jr");
    }

    @Test
    void fallsBackToUserWhenNameHasNoAlphanumerics() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        UsernameGenerator generator = new UsernameGenerator(userRepository);

        assertThat(generator.generate("!!!")).isEqualTo("user");
    }

    @Test
    void appendsNumericSuffixOnCollision() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByUsernameIgnoreCase("anna")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("anna-2")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("anna-3")).thenReturn(false);
        UsernameGenerator generator = new UsernameGenerator(userRepository);

        assertThat(generator.generate("Anna")).isEqualTo("anna-3");
    }

    @Test
    void truncatesLongNamesToTwentyFourCharacters() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        UsernameGenerator generator = new UsernameGenerator(userRepository);

        String longName = "A Very Long Dog Trainer Display Name";
        String username = generator.generate(longName);

        assertThat(username).hasSizeLessThanOrEqualTo(24);
    }
}
