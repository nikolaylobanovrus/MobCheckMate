# Game server image. Build from the repository root (the server needs the shared module):
#   docker build -f server/Dockerfile -t chess-server .
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon --console=plain :server:installDist

FROM eclipse-temurin:21-jre-noble
RUN useradd --system --uid 10001 --no-create-home chess
COPY --from=build /src/server/build/install/server /app
USER chess
ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
