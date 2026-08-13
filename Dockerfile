# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom files first for dependency caching
COPY pom.xml .
COPY domain/pom.xml domain/
COPY core/pom.xml core/
COPY web/pom.xml web/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY domain/src domain/src
COPY core/src core/src
COPY web/src web/src

# Build the application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built artifact
COPY --from=build /app/web/target/*.war app.war

# Non-root cannot reliably bind to ports <1024; use 8080
EXPOSE 8080

# Health check on port 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application on port 8080
ENTRYPOINT ["java", "-jar", "app.war", "--spring.profiles.active=prod", "--server.port=8080"]
