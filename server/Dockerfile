# Charging-advisor relay server image.
# Build the distribution first (the Gradle build also contains the Android modules, so it runs outside Docker):
#   .\gradlew.bat :server:installDist
#   docker build -t ev-advisor-server server
FROM eclipse-temurin:21-jre

RUN groupadd --system advisor && useradd --system --gid advisor --no-create-home advisor \
    && mkdir -p /app/data && chown advisor:advisor /app/data
WORKDIR /app
COPY build/install/server/lib/ /app/lib/

USER advisor
# Inside the compose network only; the reverse proxy (TRUST_PROXY) is the one public entry point.
ENV HOST=0.0.0.0 \
    PORT=8080 \
    DATA_DIR=/app/data \
    TRUST_PROXY=1
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-Xmx384m", "-XX:+ExitOnOutOfMemoryError", "-cp", "/app/lib/*", "com.example.pleosev.server.ApplicationKt"]
