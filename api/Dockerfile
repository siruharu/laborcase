# syntax=docker/dockerfile:1.7
# Slim runtime image. The bootJar is built locally via Gradle before
# `docker build` — keeping the Dockerfile simple means the image is
# insensitive to project layout churn.
#
# One image serves both roles:
#   JOB_MODE=api    → Spring Boot web server (default)
#   JOB_MODE=full   → run FullSyncJob (one-shot)
#   JOB_MODE=delta  → run DeltaSyncJob (one-shot)

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
LABEL org.opencontainers.image.source="https://github.com/siruharu/laborcase" \
      org.opencontainers.image.description="laborcase backend — API + sync jobs"

COPY api/build/libs/laborcase-api-*-SNAPSHOT.jar /app/app.jar
COPY --chmod=0755 api/docker-entrypoint.sh /entrypoint.sh

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["/entrypoint.sh"]
