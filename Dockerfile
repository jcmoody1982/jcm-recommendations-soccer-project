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

# Expose port 80 (nginx default proxy target)
EXPOSE 80

# Health check on port 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80/actuator/health || exit 1

# Run the application on port 80
ENTRYPOINT ["java", "-jar", "app.war", "--spring.profiles.active=prod", "--server.port=80"]
