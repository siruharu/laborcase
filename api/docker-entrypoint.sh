#!/bin/sh
# Dispatch between API server and sync jobs based on $JOB_MODE.
# Called by Cloud Run: the API service leaves JOB_MODE unset (defaults to
# "api"); Cloud Run Jobs set JOB_MODE to "full" or "delta".
set -eu

MODE="${JOB_MODE:-api}"

case "$MODE" in
  api)
    exec java $JAVA_OPTS -jar /app/app.jar "$@"
    ;;
  full|delta)
    # Reuses the boot JAR classpath via PropertiesLauncher but hands control
    # to SyncMain. Spring Boot 3.2+ moved the launcher into
    # org.springframework.boot.loader.launch.
    exec java $JAVA_OPTS \
      -cp /app/app.jar \
      -Dloader.main=kr.laborcase.law.sync.SyncMain \
      org.springframework.boot.loader.launch.PropertiesLauncher "$MODE"
    ;;
  *)
    echo "unknown JOB_MODE: $MODE" >&2
    exit 2
    ;;
esac
