# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Exec-form ENTRYPOINT does not expand shell variables, so rely on Spring Boot's
# built-in SERVER_PORT env var binding (defaults to 8080) instead of -D substitution.
ENTRYPOINT ["java", "-jar", "app.jar"]
