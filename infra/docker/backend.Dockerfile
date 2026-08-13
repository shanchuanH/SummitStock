FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY libs ./libs
COPY config ./config
COPY apps/backend ./apps/backend
RUN mvn -B -pl apps/backend -am -DskipTests package \
    && jar tf apps/backend/target/backend-0.1.0-SNAPSHOT.jar \
      | grep -q '^BOOT-INF/classes/strategy/STRATEGY_CONFIG_V3_DRAFT.yaml$'

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 portfolio
COPY --from=build /workspace/apps/backend/target/backend-0.1.0-SNAPSHOT.jar /app/portfolio-engine.jar
USER portfolio
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/portfolio-engine.jar"]
