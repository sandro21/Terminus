FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :server:installDist :worker:installDist -PserverOnly --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/server/build/install/server /app/server
COPY --from=build /workspace/worker/build/install/worker /app/worker
EXPOSE 8080
CMD ["/app/server/bin/server"]
