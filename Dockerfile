# ─────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# ─────────────────────────────────────────────
# Stage 2: Run
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy generated jar
COPY --from=build /app/target/*.jar app.jar

# Render provides PORT dynamically
ENV PORT=8080

EXPOSE 8080

# Run Spring Boot app
ENTRYPOINT ["java", "-Dserver.port=${PORT}", "-jar", "app.jar"]