package com.example.githubaccess.client;

import com.example.githubaccess.exception.OrganizationNotFoundException;
import com.example.githubaccess.exception.RateLimitExceededException;
import com.example.githubaccess.model.CollaboratorResponse;
import com.example.githubaccess.model.RepositoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

@Component
public class GithubApiClient {

        private static final Logger log = LoggerFactory.getLogger(GithubApiClient.class);

        private final WebClient webClient;
        private final RateLimitGuard rateLimitGuard;
        private final int perPage;

        public GithubApiClient(WebClient githubWebClient,
                        RateLimitGuard rateLimitGuard,
                        @Value("${github.api.per-page:100}") int perPage) {
                this.webClient = githubWebClient;
                this.rateLimitGuard = rateLimitGuard;
                this.perPage = perPage;
        }

        public Flux<RepositoryResponse> fetchOrgRepositories(String org) {
                return rateLimitGuard.checkOrWait()
                                .thenMany(paginateFlux(page -> {
                                        log.debug("Fetching repos for org '{}', page {}", org, page);
                                        return webClient.get()
                                                        .uri("/orgs/{org}/repos?type=all&per_page={perPage}&page={page}",
                                                                        org, perPage, page)
                                                        .retrieve()
                                                        .onStatus(
                                                                        status -> status == HttpStatus.NOT_FOUND,
                                                                        response -> response.createException()
                                                                                        .map(ex -> new OrganizationNotFoundException(org)))
                                                        .onStatus(
                                                                        status -> status == HttpStatus.UNAUTHORIZED,
                                                                        response -> response.createException()
                                                                                        .map(ex -> new SecurityException(
                                                                                                        "Invalid or missing GitHub token")))
                                                        .onStatus(
                                                                        status -> status == HttpStatus.FORBIDDEN,
                                                                        response -> {
                                                                                String remaining = response.headers()
                                                                                                .asHttpHeaders()
                                                                                                .getFirst("X-RateLimit-Remaining");
                                                                                if ("0".equals(remaining)) {
                                                                                        long resetEpoch = parseResetEpoch(response);
                                                                                        log.warn("Rate limit hit fetching repos for org '{}'",
                                                                                                        org);
                                                                                        return Mono.error(
                                                                                                        new RateLimitExceededException(
                                                                                                                        resetEpoch));
                                                                                }
                                                                                return response.createException()
                                                                                                .map(ex -> new SecurityException(
                                                                                                                "Forbidden accessing org repos: "
                                                                                                                                + org));
                                                                        })
                                                        .bodyToFlux(RepositoryResponse.class)
                                                        .collectList();
                                }));
        }

        public Flux<CollaboratorResponse> fetchRepositoryCollaborators(String org, String repoName) {
                return rateLimitGuard.checkOrWait()
                                .thenMany(paginateFlux(page -> {
                                        log.debug("Fetching collaborators for {}/{}, page {}", org, repoName, page);
                                        return webClient.get()
                                                         .uri("/repos/{org}/{repo}/collaborators?affiliation=all&per_page={perPage}&page={page}",
                                                                         org, repoName, perPage, page)
                                                         .retrieve()
                                                         .onStatus(
                                                                         status -> status == HttpStatus.UNAUTHORIZED,
                                                                         response -> response.createException()
                                                                                         .map(ex -> new SecurityException(
                                                                                                         "Invalid or missing GitHub token")))
                                                         .onStatus(
                                                                         status -> status == HttpStatus.FORBIDDEN,
                                                                         response -> {
                                                                                 String remaining = response.headers()
                                                                                                 .asHttpHeaders()
                                                                                                 .getFirst("X-RateLimit-Remaining");
                                                                                 if ("0".equals(remaining)) {
                                                                                         long resetEpoch = parseResetEpoch(response);
                                                                                         return Mono.error(
                                                                                                         new RateLimitExceededException(
                                                                                                                         resetEpoch));
                                                                                 }
                                                                                 // Non-rate-limit 403 (e.g. no push
                                                                                 // access): surface the real exception
                                                                                 // so the onErrorResume below can
                                                                                 // cleanly swallow it.
                                                                                 return response.createException();
                                                                         })
                                                         .bodyToFlux(CollaboratorResponse.class)
                                                         .collectList()
                                                         .onErrorResume(
                                                                         ex -> {
                                                                                 if (ex instanceof WebClientResponseException wcre) {
                                                                                         return wcre.getStatusCode() == HttpStatus.FORBIDDEN
                                                                                                         || wcre.getStatusCode() == HttpStatus.NOT_FOUND;
                                                                                 }
                                                                                 return false;
                                                                         },
                                                                         ex -> {
                                                                                 log.debug("Skipping collaborators for {}/{}: {}",
                                                                                                 org, repoName,
                                                                                                 ex.getMessage());
                                                                                 return Mono.just(List.of());
                                                                         });
                                 }));
        }

        private long parseResetEpoch(ClientResponse response) {
                String reset = response.headers().asHttpHeaders().getFirst("X-RateLimit-Reset");
                if (reset != null) {
                        try {
                                return Long.parseLong(reset);
                        } catch (NumberFormatException ignored) {
                        }
                }
                return 0L;
        }

        private <T> Flux<T> paginateFlux(Function<Integer, Mono<List<T>>> pageFetcher) {
                return Flux.range(1, Integer.MAX_VALUE)
                                .concatMap(pageFetcher)
                                .takeUntil(page -> page.size() < perPage)
                                .flatMap(Flux::fromIterable);
        }
}
