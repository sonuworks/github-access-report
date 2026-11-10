package com.example.githubaccess.service;

import com.example.githubaccess.client.GithubApiClient;
import com.example.githubaccess.model.AccessReportResponse;
import com.example.githubaccess.model.CollaboratorResponse;
import com.example.githubaccess.model.RepositoryResponse;
import com.example.githubaccess.model.UserAccessReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessReportServiceTest {

    @Mock
    private GithubApiClient githubApiClient;

    private AccessReportService accessReportService;

    @BeforeEach
    void setUp() {
        accessReportService = new AccessReportService(githubApiClient, 20);
    }

    @Test
    void shouldBuildReportAggregatedAndAlphabeticallySorted() {
        String org = "test-org";
        RepositoryResponse repo1 = new RepositoryResponse("repo-b", "test-org/repo-b", false);
        RepositoryResponse repo2 = new RepositoryResponse("repo-a", "test-org/repo-a", true);

        when(githubApiClient.fetchOrgRepositories(org)).thenReturn(Flux.just(repo1, repo2));
        when(githubApiClient.fetchRepositoryCollaborators(org, "repo-b"))
                .thenReturn(Flux.just(new CollaboratorResponse("bob", 1), new CollaboratorResponse("alice", 2)));
        when(githubApiClient.fetchRepositoryCollaborators(org, "repo-a"))
                .thenReturn(Flux.just(new CollaboratorResponse("alice", 2), new CollaboratorResponse("charlie", 3)));

        StepVerifier.create(accessReportService.generateReport(org))
                .assertNext(report -> {
                    assertThat(report.organization()).isEqualTo(org);
                    List<UserAccessReport> users = report.users();
                    assertThat(users).hasSize(3);

                    // Alphabetically sorted usernames: alice, bob, charlie
                    assertThat(users.get(0).username()).isEqualTo("alice");
                    assertThat(users.get(0).repositories()).containsExactly("repo-a", "repo-b"); // repos sorted alphabetically

                    assertThat(users.get(1).username()).isEqualTo("bob");
                    assertThat(users.get(1).repositories()).containsExactly("repo-b");

                    assertThat(users.get(2).username()).isEqualTo("charlie");
                    assertThat(users.get(2).repositories()).containsExactly("repo-a");
                })
                .verifyComplete();

        verify(githubApiClient, times(1)).fetchOrgRepositories(org);
        verify(githubApiClient, times(1)).fetchRepositoryCollaborators(org, "repo-b");
        verify(githubApiClient, times(1)).fetchRepositoryCollaborators(org, "repo-a");
    }

    @Test
    void shouldCacheReportAndReturnOnSubsequentCalls() {
        String org = "cached-org";
        RepositoryResponse repo = new RepositoryResponse("repo-1", "cached-org/repo-1", false);

        when(githubApiClient.fetchOrgRepositories(org)).thenReturn(Flux.just(repo));
        when(githubApiClient.fetchRepositoryCollaborators(org, "repo-1"))
                .thenReturn(Flux.just(new CollaboratorResponse("alice", 1)));

        // First call - cache miss
        AccessReportResponse first = accessReportService.generateReport(org).block();
        assertThat(first).isNotNull();

        // Second call - cache hit
        AccessReportResponse second = accessReportService.generateReport(org).block();
        assertThat(second).isNotNull();
        assertThat(second).isSameAs(first);

        // Underlying client should only have been called once
        verify(githubApiClient, times(1)).fetchOrgRepositories(org);
        verify(githubApiClient, times(1)).fetchRepositoryCollaborators(org, "repo-1");
    }

    @Test
    void shouldBypassCacheWhenForceRefreshIsTrue() {
        String org = "refresh-org";
        RepositoryResponse repo = new RepositoryResponse("repo-1", "refresh-org/repo-1", false);

        when(githubApiClient.fetchOrgRepositories(org)).thenReturn(Flux.just(repo));
        when(githubApiClient.fetchRepositoryCollaborators(org, "repo-1"))
                .thenReturn(Flux.just(new CollaboratorResponse("alice", 1)));

        // First call
        accessReportService.generateReport(org, false).block();

        // Second call with forceRefresh=true
        accessReportService.generateReport(org, true).block();

        // Client should have been called twice
        verify(githubApiClient, times(2)).fetchOrgRepositories(org);
        verify(githubApiClient, times(2)).fetchRepositoryCollaborators(org, "repo-1");
    }

    @Test
    void shouldHandleOrgWithNoRepositories() {
        String org = "empty-org";
        when(githubApiClient.fetchOrgRepositories(org)).thenReturn(Flux.empty());

        StepVerifier.create(accessReportService.generateReport(org))
                .assertNext(report -> {
                    assertThat(report.organization()).isEqualTo(org);
                    assertThat(report.users()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldPropagateErrorAndNotCache() {
        String org = "failing-org";
        when(githubApiClient.fetchOrgRepositories(org))
                .thenReturn(Flux.error(new RuntimeException("API error")));

        StepVerifier.create(accessReportService.generateReport(org))
                .expectError(RuntimeException.class)
                .verify();

        // If next call succeeds, it should call client again
        when(githubApiClient.fetchOrgRepositories(org)).thenReturn(Flux.empty());
        StepVerifier.create(accessReportService.generateReport(org))
                .assertNext(report -> assertThat(report.users()).isEmpty())
                .verifyComplete();

        verify(githubApiClient, times(2)).fetchOrgRepositories(org);
    }
}

