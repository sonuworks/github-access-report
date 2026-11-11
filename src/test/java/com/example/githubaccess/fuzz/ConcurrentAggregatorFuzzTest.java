package com.example.githubaccess.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.example.githubaccess.util.ConcurrentAggregator;
import org.junit.jupiter.api.Tag;

public class ConcurrentAggregatorFuzzTest {

    @FuzzTest(maxDuration = "5s")
    @Tag("fuzz")
    public void fuzzAggregator(FuzzedDataProvider data) {
        ConcurrentAggregator aggregator = new ConcurrentAggregator();
        int count = data.consumeInt(0, 50);
        for (int i = 0; i < count; i++) {
            String user = data.consumeString(30);
            String repo = data.consumeString(30);
            aggregator.addAccess(user, repo);
        }
        aggregator.getSnapshot();
    }
}
