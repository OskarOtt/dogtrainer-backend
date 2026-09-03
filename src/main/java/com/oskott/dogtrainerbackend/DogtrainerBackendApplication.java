package com.oskott.dogtrainerbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DogtrainerBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DogtrainerBackendApplication.class, args);
    }

}
