package com.example.githubaccess.client;

import com.example.githubaccess.exception.OrganizationNotFoundException;
import com.example.githubaccess.exception.RateLimitExceededException;
import com.example.githubaccess.model.CollaboratorResponse;
import com.example.githubaccess.model.RepositoryResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GithubApiClientTest {

    private MockWebServer mockWebServer;
    private GithubApiClient githubApiClient;
    private RateLimitGuard rateLimitGuard;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        rateLimitGuard = new RateLimitGuard();
        githubApiClient = new GithubApiClient(webClient, rateLimitGuard, 2);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldFetchOrgRepositoriesAndStopWhenPageUnderPerPage() throws InterruptedException {
        // Page 1 returns 1 repo (< perPage=2), so pagination must stop after page 1
        String json = """
                [
                    {"name": "repo-1", "full_name": "my-org/repo-1", "private": false}
                ]
                """;
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(json));

        StepVerifier.create(githubApiClient.fetchOrgRepositories("my-org"))
                .assertNext(repo -> {
                    assertThat(repo.name()).isEqualTo("repo-1");
                    assertThat(repo.fullName()).isEqualTo("my-org/repo-1");
                    assertThat(repo.isPrivate()).isFalse();
                })
                .verifyComplete();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/orgs/my-org/repos?type=all&per_page=2&page=1");
    }

    @Test
    void shouldPaginateAcrossMultiplePages() throws InterruptedException {
        // Page 1 returns 2 repos (== perPage=2), continues to page 2
        String page1 = """
                [
                    {"name": "repo-1", "full_name": "my-org/repo-1", "private": false},
                    {"name": "repo-2", "full_name": "my-org/repo-2", "private": true}
                ]
                """;
        // Page 2 returns 1 repo (< perPage=2), terminates!
        String page2 = """
                [
                    {"name": "repo-3", "full_name": "my-org/repo-3", "private": false}
                ]
                """;

        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(page1));
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(page2));

        StepVerifier.create(githubApiClient.fetchOrgRepositories("my-org"))
                .expectNextCount(3)
                .verifyComplete();

        // Exactly 2 requests should be made, no redundant 3rd empty call
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    void shouldThrowOrganizationNotFoundExceptionOn404() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        StepVerifier.create(githubApiClient.fetchOrgRepositories("unknown-org"))
                .expectError(OrganizationNotFoundException.class)
                .verify();
    }

    @Test
    void shouldThrowSecurityExceptionOn401() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));

        StepVerifier.create(githubApiClient.fetchOrgRepositories("secret-org"))
                .expectError(SecurityException.class)
                .verify();
    }

    @Test
    void shouldThrowRateLimitExceededExceptionOnRateLimited403() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1750000000"));

        StepVerifier.create(githubApiClient.fetchOrgRepositories("my-org"))
                .expectError(RateLimitExceededException.class)
                .verify();
    }

    @Test
    void shouldFetchRepositoryCollaboratorsSuccessfully() {
        String json = """
                [
                    {"login": "alice", "id": 101},
                    {"login": "bob", "id": 102}
                ]
                """;
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(json));
        // Page 2 returns empty, completing pagination
        mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("[]"));

        StepVerifier.create(githubApiClient.fetchRepositoryCollaborators("my-org", "repo-1"))
                .expectNextMatches(c -> c.login().equals("alice") && c.id() == 101)
                .expectNextMatches(c -> c.login().equals("bob") && c.id() == 102)
                .verifyComplete();
    }

    @Test
    void shouldGracefullySkipCollaboratorsOn403WithoutRateLimit() {
        // e.g. lack of push/admin access on a specific repo
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "4950")
                .setBody("{\"message\": \"Must have push access to view repository collaborators.\"}"));

        StepVerifier.create(githubApiClient.fetchRepositoryCollaborators("my-org", "restricted-repo"))
                .verifyComplete();
    }

    @Test
    void shouldGracefullySkipCollaboratorsOn404() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        StepVerifier.create(githubApiClient.fetchRepositoryCollaborators("my-org", "missing-repo"))
                .verifyComplete();
    }

    @Test
    void shouldThrowSecurityExceptionOnCollaborators401() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));

        StepVerifier.create(githubApiClient.fetchRepositoryCollaborators("my-org", "repo-1"))
                .expectError(SecurityException.class)
                .verify();
    }
}

