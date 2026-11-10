package com.example.githubaccess.client;

import com.example.githubaccess.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;

class RateLimitGuardTest {

    private RateLimitGuard rateLimitGuard;

    @BeforeEach
    void setUp() {
        rateLimitGuard = new RateLimitGuard();
    }

    @Test
    void shouldCompleteImmediatelyWhenQuotaRemaining() {
        rateLimitGuard.update(100, Instant.now().getEpochSecond() + 3600);

        StepVerifier.create(rateLimitGuard.checkOrWait())
                .verifyComplete();
    }

    @Test
    void shouldFailFastWhenQuotaExhaustedAndResetExceedsThreshold() {
        long resetEpoch = Instant.now().getEpochSecond() + 120; // 120s > 60s threshold
        rateLimitGuard.update(0, resetEpoch);

        StepVerifier.create(rateLimitGuard.checkOrWait())
                .expectError(RateLimitExceededException.class)
                .verify();
    }

    @Test
    void shouldCompleteWhenResetAlreadyPassed() {
        long resetEpoch = Instant.now().getEpochSecond() - 10; // Already in the past
        rateLimitGuard.update(0, resetEpoch);

        StepVerifier.create(rateLimitGuard.checkOrWait())
                .verifyComplete();
    }
}

