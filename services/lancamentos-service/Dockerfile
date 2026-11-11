# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN chmod +x mvnw \
    && ./mvnw -q -DskipTests package \
    && java -Djarmode=tools -jar target/lancamentos-service.jar extract --layers --launcher --destination extracted \
    && mkdir -p extracted/snapshot-dependencies \
    && touch extracted/snapshot-dependencies/.keep

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget su-exec \
    && addgroup -S app \
    && adduser -S app -G app
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./
# O volume /tmp do Fargate nasce root:root. O entrypoint abre o diretorio e desce para app.
USER root
ENV SERVER_PORT=8081 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O - "http://127.0.0.1:${SERVER_PORT}/actuator/health/liveness" >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "chmod 1777 /tmp && exec su-exec app java org.springframework.boot.loader.launch.JarLauncher"]
