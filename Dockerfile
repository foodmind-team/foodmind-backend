FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update \
    && apt-get upgrade --yes \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home foodmind
WORKDIR /app
COPY --from=build /workspace/target/foodmind-backend-*.jar /app/foodmind-backend.jar
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/foodmind-backend.jar"]
