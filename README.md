<div align="center">

# GitHub Access Report Service

A production-quality **Spring Boot service** that connects to the GitHub API and generates a report showing **which users have access to which repositories** inside a GitHub organization.

Built for reliability, scalability, and clean API design.

<br>

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.13-brightgreen)
![WebFlux](https://img.shields.io/badge/WebFlux-Reactive-blue)
![Build](https://img.shields.io/badge/Maven-Build-red)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

</div>

---

# Overview

Organizations often need visibility into **who has access to which repositories** across their GitHub organization.

This service connects to the **GitHub REST API** and generates an **aggregated access report** mapping users to the repositories they can access.

It is designed to scale efficiently even for large organizations with:

- **100+ repositories**
- **1000+ collaborators**

The service exposes a clean REST API that returns the report in **structured JSON format**.

---

# Features

| Feature | Description |
|------|-------------|
| GitHub API Integration | Connects securely to GitHub using a personal access token |
| Repository Discovery | Fetches all repositories within an organization |
| Access Mapping | Determines which users have access to each repository |
| Aggregated Reporting | Produces a user → repository mapping |
| High Concurrency | Fetches collaborator data in parallel |
| Pagination Support | Automatically handles GitHub API pagination |
| Thread-Safe Aggregation | Uses concurrent structures to support parallel processing |
| Structured Error Handling | RFC 7807 ProblemDetail responses |

---

# Table of Contents

- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Running the Project](#running-the-project)
- [API Usage](#api-usage)
- [Architecture & Design Decisions](#architecture--design-decisions)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)

---

# Prerequisites

Before running the project ensure you have:

- **Java 21+**
- **Maven 3.8+**
- A **GitHub Personal Access Token**

Required GitHub scopes:

```

read:org
repo

````

---

# Configuration

The service reads the GitHub token from an **environment variable**.

### Windows (PowerShell)

```powershell
$env:GITHUB_TOKEN = "ghp_your_token_here"
````

### Linux / macOS

```bash
export GITHUB_TOKEN=ghp_your_token_here
```

The `application.yml` binds this value automatically:

```yaml
github:
  api:
    token: ${GITHUB_TOKEN:}
    concurrency: 20
```

---

# Running the Project

### Using Maven

```bash
mvn spring-boot:run
```

### Running Tests

```bash
mvn test
```

### Using Docker

```bash
# Build Docker image
docker build -t github-access-report .

# Run container
docker run -p 8080:8080 -e GITHUB_TOKEN="ghp_your_token_here" github-access-report
```

The server starts on:

```
http://localhost:8080
```

Health check endpoint:
```
http://localhost:8080/actuator/health
```

---

# GitHub Packages & Registries

This project is configured to publish automatically to **GitHub Packages**:

### 1. Docker Container (`ghcr.io`)

Pull and run directly from GitHub Container Registry:

```bash
docker pull ghcr.io/sonuworks/github-access-report:latest
docker run -p 8080:8080 -e GITHUB_TOKEN="ghp_your_token_here" ghcr.io/sonuworks/github-access-report:latest
```

### 2. Apache Maven (`maven.pkg.github.com`)

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.sonuworks</groupId>
    <artifactId>github-access</artifactId>
    <version>1.0.0</version>
</dependency>
```

Add the GitHub repository to your `pom.xml` or `~/.m2/settings.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/sonuworks/github-access-report</url>
    </repository>
</repositories>
```

---

# API Usage

## Endpoint

```
GET /api/access-report?org={organization}&forceRefresh={boolean}
```

| Parameter      | Type    | Required | Default | Description                                   |
| -------------- | ------- | -------- | ------- | --------------------------------------------- |
| `org`          | String  | Yes      | -       | GitHub organization login name                |
| `forceRefresh` | Boolean | No       | `false` | When true, evicts cache and fetches fresh data |

---

## Example Requests

```bash
# Standard cached request (10-minute cache)
curl "http://localhost:8080/api/access-report?org=my-org"

# Force cache bypass & fresh report
curl "http://localhost:8080/api/access-report?org=my-org&forceRefresh=true"
```

---

## Example Response

```json
{
  "organization": "my-org",
  "users": [
    {
      "username": "alice",
      "repositories": ["backend-api", "frontend-app"]
    },
    {
      "username": "bob",
      "repositories": ["backend-api"]
    }
  ]
}
```

---

# Error Responses

All errors follow **RFC 7807 ProblemDetail format**.

| Scenario                 | HTTP Status             |
| ------------------------ | ----------------------- |
| Organization not found   | 404                     |
| Invalid or missing token | 401                     |
| GitHub API error         | Mirrors GitHub response |
| Blank `org` parameter    | 400                     |

---

# Architecture & Design Decisions

## Reactive Pipeline with Bounded Concurrency

The service uses a **reactive processing pipeline** built with Spring WebFlux.

```
fetchOrgRepositories(org)
   └─ flatMap(repo -> fetchCollaborators(repo), concurrency=20)
         └─ aggregator.addAccess(user, repo)
```

The concurrency limit of **20 parallel requests** allows fast processing for large organizations while avoiding GitHub rate-limit exhaustion.

---

## Pagination

GitHub returns paginated results.

`GithubApiClient.paginateFlux()` automatically continues requesting pages until GitHub returns an empty response.

The `per_page` value is configurable in `application.yml`.

Default:

```
per_page = 100
```

Which is the maximum supported by GitHub.

---

## Thread-Safe Aggregation

Because collaborator requests run concurrently, aggregation must be thread-safe.

`ConcurrentAggregator` uses:

```
ConcurrentHashMap
computeIfAbsent
Collections.synchronizedList
```

This ensures safe writes from multiple Reactor threads.

---

## Error Handling

Centralized error handling is implemented using:

```
GlobalExceptionHandler
```

It converts domain exceptions and GitHub API errors into structured **ProblemDetail responses**.

Special cases:

* `403 Forbidden`
* `404 Not Found`

on collaborator endpoints are ignored to avoid failing the entire report.

---

# Tech Stack

| Component   | Technology               |
| ----------- | ------------------------ |
| Framework   | Spring Boot 3.4.13       |
| Language    | Java 21                  |
| HTTP Client | Spring WebFlux WebClient |
| JSON        | Jackson                  |
| Build Tool  | Maven                    |

---

# Project Structure

```
src/main/java/com/example/githubaccess/

├── GithubAccessApplication.java

├── config/
│   └── GithubConfig.java

├── controller/
│   └── AccessReportController.java

├── service/
│   └── AccessReportService.java

├── client/
│   ├── GithubApiClient.java
│   └── RateLimitGuard.java

├── model/
│   ├── RepositoryResponse.java
│   ├── CollaboratorResponse.java
│   ├── UserAccessReport.java
│   └── AccessReportResponse.java

├── util/
│   └── ConcurrentAggregator.java

└── exception/
    ├── GlobalExceptionHandler.java
    ├── OrganizationNotFoundException.java
    └── RateLimitExceededException.java

src/main/resources/
└── application.yml

src/test/java/com/example/githubaccess/
├── GithubAccessApplicationTests.java
├── client/
│   ├── GithubApiClientTest.java
│   └── RateLimitGuardTest.java
├── controller/
│   └── AccessReportControllerTest.java
├── service/
│   └── AccessReportServiceTest.java
└── util/
    └── ConcurrentAggregatorTest.java
```

---

# Contributing

Contributions are welcome. If you'd like to improve the project:

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Open a pull request

---

# License

This project is released under the **MIT License**.

---

# Author

Created by **Sonu Jana**

If this project helps you, consider giving it a ⭐ on GitHub.
