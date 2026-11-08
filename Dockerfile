# ---- Stage 1: build ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies in their own layer so source edits don't re-download them.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run as part of the image build: a broken settlement calculation never ships.
RUN mvn -B package && cp target/settleup-*.jar app.jar

# ---- Stage 2: runtime -------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/app.jar app.jar
USER app

# Drives the service-charge percentage; override at `docker run -e GITHUB_USERNAME=...`.
ENV GITHUB_USERNAME=CHANGE_ME
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
