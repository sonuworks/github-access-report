package com.example.githubaccess.controller;

import com.example.githubaccess.exception.GlobalExceptionHandler;
import com.example.githubaccess.exception.OrganizationNotFoundException;
import com.example.githubaccess.exception.RateLimitExceededException;
import com.example.githubaccess.model.AccessReportResponse;
import com.example.githubaccess.model.UserAccessReport;
import com.example.githubaccess.service.AccessReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccessReportController.class)
@Import(GlobalExceptionHandler.class)
class AccessReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessReportService accessReportService;

    @Test
    void shouldReturn200AndReportWhenOrgIsValid() throws Exception {
        AccessReportResponse response = new AccessReportResponse(
                "my-org",
                List.of(new UserAccessReport("alice", List.of("repo-1", "repo-2")))
        );

        when(accessReportService.generateReport("my-org", false)).thenReturn(Mono.just(response));

        MvcResult mvcResult = mockMvc.perform(get("/api/access-report").param("org", "my-org"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.organization").value("my-org"))
                .andExpect(jsonPath("$.users[0].username").value("alice"))
                .andExpect(jsonPath("$.users[0].repositories[0]").value("repo-1"))
                .andExpect(jsonPath("$.users[0].repositories[1]").value("repo-2"));
    }

    @Test
    void shouldPassForceRefreshParameterToService() throws Exception {
        AccessReportResponse response = new AccessReportResponse("my-org", List.of());
        when(accessReportService.generateReport("my-org", true)).thenReturn(Mono.just(response));

        MvcResult mvcResult = mockMvc.perform(get("/api/access-report")
                        .param("org", "my-org")
                        .param("forceRefresh", "true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());

        verify(accessReportService).generateReport("my-org", true);
    }

    @Test
    void shouldReturn400WhenOrgParameterIsBlank() throws Exception {
        mockMvc.perform(get("/api/access-report").param("org", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn400WhenOrgParameterIsMissing() throws Exception {
        mockMvc.perform(get("/api/access-report"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn404WhenOrganizationNotFound() throws Exception {
        when(accessReportService.generateReport("non-existent-org", false))
                .thenReturn(Mono.error(new OrganizationNotFoundException("non-existent-org")));

        MvcResult mvcResult = mockMvc.perform(get("/api/access-report").param("org", "non-existent-org"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Organization not found: non-existent-org"));
    }

    @Test
    void shouldReturn401WhenSecurityExceptionThrown() throws Exception {
        when(accessReportService.generateReport("private-org", false))
                .thenReturn(Mono.error(new SecurityException("Invalid or missing GitHub token")));

        MvcResult mvcResult = mockMvc.perform(get("/api/access-report").param("org", "private-org"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid or missing GitHub token"));
    }

    @Test
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        when(accessReportService.generateReport("busy-org", false))
                .thenReturn(Mono.error(new RateLimitExceededException(1700000000L)));

        MvcResult mvcResult = mockMvc.perform(get("/api/access-report").param("org", "busy-org"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.retryAfter").exists());
    }
}
