FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S codex && adduser -S codex -G codex \
    && mkdir -p /app/data/sources \
    && chown -R codex:codex /app
COPY --from=build --chown=codex:codex /workspace/target/codex-of-realms-backend-*.jar app.jar

USER codex
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
