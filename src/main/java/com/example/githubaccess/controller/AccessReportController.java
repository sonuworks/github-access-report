package com.example.githubaccess.controller;

import com.example.githubaccess.model.AccessReportResponse;
import com.example.githubaccess.service.AccessReportService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@Validated
public class AccessReportController {

    private final AccessReportService accessReportService;

    public AccessReportController(AccessReportService accessReportService) {
        this.accessReportService = accessReportService;
    }

    @GetMapping("/access-report")
    public Mono<ResponseEntity<AccessReportResponse>> getAccessReport(
            @RequestParam @NotBlank(message = "org parameter must not be blank") String org,
            @RequestParam(defaultValue = "false") boolean forceRefresh
    ) {
        return accessReportService.generateReport(org.trim(), forceRefresh)
                .map(ResponseEntity::ok);
    }
}
