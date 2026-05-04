# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy everything
COPY . .

# Build jar (skip tests to avoid junit issues)
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy jar from builder
COPY --from=builder /app/target/*.jar app.jar

# Expose port (Render uses PORT env)
EXPOSE 8080

# Run app
ENTRYPOINT ["java", "-jar", "app.jar"]
