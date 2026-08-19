# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true package \
    && cp target/uengine-analytics-*.jar /workspace/analytics.jar

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=build /workspace/analytics.jar /app/analytics.jar

EXPOSE 9095

ENV JAVA_OPTS="-Xms128m -Xmx512m -Dfile.encoding=UTF-8" \
    ANALYTICS_SERVICE_PORT="9095" \
    POSTGRES_HOST="postgres" \
    POSTGRES_PORT="5432" \
    POSTGRES_DB="uengine" \
    POSTGRES_USER="uengine" \
    POSTGRES_PASSWORD="uengine" \
    UENGINE_DB_SCHEMA="public" \
    UENGINE_ANALYTICS_ETL_ENABLED="true" \
    UENGINE_ANALYTICS_ETL_INTERVAL_MS="60000" \
    UENGINE_ANALYTICS_ETL_INITIAL_DELAY_MS="30000" \
    UENGINE_ANALYTICS_ETL_TIME_ZONE="Asia/Seoul"

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/9095'

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/analytics.jar"]
