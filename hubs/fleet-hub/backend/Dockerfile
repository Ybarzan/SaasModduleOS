FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests
# Tests are run in CI (ci.yml) before this Docker build.

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Pas de root dans le conteneur : l'application n'écrit rien sur disque (logs sur stdout).
RUN useradd --system --no-create-home --uid 10001 fleethub
USER fleethub
EXPOSE 8090
# Le tas suit la mémoire allouée au conteneur plutôt que celle de la machine.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
