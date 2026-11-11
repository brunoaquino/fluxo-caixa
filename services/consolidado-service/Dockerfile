FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN chmod +x mvnw && ./mvnw -q -DskipTests package \
    && java -Djarmode=tools -jar target/consolidado-service.jar extract --layers --launcher --destination extracted \
    && mkdir -p extracted/snapshot-dependencies \
    && touch extracted/snapshot-dependencies/.keep

FROM eclipse-temurin:25-jre-noble
RUN apt-get update \
    && apt-get upgrade -y --no-install-recommends \
    && apt-get install -y --no-install-recommends wget util-linux \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./
# O volume /tmp do Fargate nasce root:root. O entrypoint abre o diretorio e desce para app.
USER root
EXPOSE 8082
ENTRYPOINT ["bash", "-c", "chmod 1777 /tmp && exec runuser --user app -- java org.springframework.boot.loader.launch.JarLauncher"]
