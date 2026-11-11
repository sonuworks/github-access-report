package com.example.githubaccess.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.example.githubaccess.client.RateLimitGuard;
import org.junit.jupiter.api.Tag;

public class RateLimitGuardFuzzTest {

    @FuzzTest(maxDuration = "5s")
    @Tag("fuzz")
    public void fuzzRateLimitGuardUpdateAndCheck(FuzzedDataProvider data) {
        RateLimitGuard guard = new RateLimitGuard();
        int remaining = data.consumeInt();
        long resetEpoch = data.consumeLong();

        guard.update(remaining, resetEpoch);
        try {
            guard.checkOrWait().block();
        } catch (Exception ignored) {
            // Expected for fail-fast RateLimitExceededException
        }
    }
}
