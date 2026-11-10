package com.example.githubaccess.util;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentAggregator {

    private final ConcurrentHashMap<String, Set<String>> userRepoMap = new ConcurrentHashMap<>();

    public void addAccess(String username, String repositoryName) {
        if (username != null && repositoryName != null) {
            userRepoMap.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet())
                    .add(repositoryName);
        }
    }

    public Map<String, Set<String>> getSnapshot() {
        return Collections.unmodifiableMap(userRepoMap);
    }
}
