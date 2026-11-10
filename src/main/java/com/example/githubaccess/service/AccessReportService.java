package com.example.githubaccess.service;

import com.example.githubaccess.client.GithubApiClient;
import com.example.githubaccess.model.AccessReportResponse;
import com.example.githubaccess.model.UserAccessReport;
import com.example.githubaccess.util.ConcurrentAggregator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AccessReportService {

    private static final Logger log = LoggerFactory.getLogger(AccessReportService.class);

    private final GithubApiClient githubApiClient;
    private final int concurrency;

    private final Cache<String, AccessReportResponse> reportCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(100)
            .build();

    public AccessReportService(GithubApiClient githubApiClient,
                               @Value("${github.api.concurrency:20}") int concurrency) {
        this.githubApiClient = githubApiClient;
        this.concurrency = concurrency;
    }

    public Mono<AccessReportResponse> generateReport(String org) {
        return generateReport(org, false);
    }

    public Mono<AccessReportResponse> generateReport(String org, boolean forceRefresh) {
        if (!forceRefresh) {
            AccessReportResponse cached = reportCache.getIfPresent(org);
            if (cached != null) {
                log.info("Returning cached report for org '{}'", org);
                return Mono.just(cached);
            }
        } else {
            reportCache.invalidate(org);
            log.info("Force refresh requested for org '{}' (evicted from cache)", org);
        }

        log.info("Building access report for org '{}' (cache miss, concurrency={})", org, concurrency);
        ConcurrentAggregator aggregator = new ConcurrentAggregator();

        return githubApiClient.fetchOrgRepositories(org)
                .flatMap(repo -> githubApiClient.fetchRepositoryCollaborators(org, repo.name())
                        .doOnNext(collaborator -> aggregator.addAccess(collaborator.login(), repo.name()))
                        .then(),
                        concurrency)
                .then(Mono.fromCallable(() -> {
                    AccessReportResponse report = buildResponse(org, aggregator);
                    reportCache.put(org, report);
                    log.info("Report for org '{}' built and cached: {} users across repositories",
                            org, report.users().size());
                    return report;
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private AccessReportResponse buildResponse(String org, ConcurrentAggregator aggregator) {
        List<UserAccessReport> users = new ArrayList<>();
        aggregator.getSnapshot().forEach((username, repos) -> {
            List<String> sortedRepos = new ArrayList<>(repos);
            sortedRepos.sort(Comparator.naturalOrder());
            users.add(new UserAccessReport(username, sortedRepos));
        });
        users.sort(Comparator.comparing(UserAccessReport::username));
        return new AccessReportResponse(org, users);
    }
}
