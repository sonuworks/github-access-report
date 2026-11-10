package com.example.githubaccess.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentAggregatorTest {

    private ConcurrentAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new ConcurrentAggregator();
    }

    @Test
    void shouldAddUserAccess() {
        aggregator.addAccess("alice", "repo-1");
        aggregator.addAccess("alice", "repo-2");
        aggregator.addAccess("bob", "repo-1");

        Map<String, Set<String>> snapshot = aggregator.getSnapshot();

        assertThat(snapshot).containsOnlyKeys("alice", "bob");
        assertThat(snapshot.get("alice")).containsExactlyInAnyOrder("repo-1", "repo-2");
        assertThat(snapshot.get("bob")).containsExactly("repo-1");
    }

    @Test
    void shouldDeduplicateRepositoriesForSameUser() {
        aggregator.addAccess("alice", "repo-1");
        aggregator.addAccess("alice", "repo-1");

        Map<String, Set<String>> snapshot = aggregator.getSnapshot();

        assertThat(snapshot.get("alice")).hasSize(1);
        assertThat(snapshot.get("alice")).containsExactly("repo-1");
    }

    @Test
    void shouldIgnoreNullInputsGracefully() {
        aggregator.addAccess(null, "repo-1");
        aggregator.addAccess("alice", null);

        Map<String, Set<String>> snapshot = aggregator.getSnapshot();
        assertThat(snapshot).isEmpty();
    }

    @Test
    void shouldHandleConcurrentAccessSafely() throws InterruptedException {
        int threadCount = 10;
        int reposPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int r = 0; r < reposPerThread; r++) {
                        aggregator.addAccess("user-" + (r % 5), "repo-" + threadId + "-" + r);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        Map<String, Set<String>> snapshot = aggregator.getSnapshot();
        assertThat(snapshot).hasSize(5);

        int totalRepos = snapshot.values().stream().mapToInt(Set::size).sum();
        assertThat(totalRepos).isEqualTo(threadCount * reposPerThread);
    }
}

