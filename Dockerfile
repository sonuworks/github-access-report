# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/target/github-access-1.0.0.jar app.jar

ENV GITHUB_TOKEN=""
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

