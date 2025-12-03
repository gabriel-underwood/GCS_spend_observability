# Use Eclipse Temurin JDK 25 (OpenJDK distribution)
FROM eclipse-temurin:21-jdk-alpine AS build

# Set working directory
WORKDIR /app

# Copy Maven files
COPY pom.xml .

# Copy source code
COPY src ./src

# Install Maven
RUN apk add --no-cache maven

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage - use JRE for smaller image
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the uber JAR from build stage
COPY --from=build /app/target/gcs-storage-metrics-1.0.0.jar app.jar

# Set environment variables with defaults
ENV PORT=8080
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Expose port
EXPOSE ${PORT}

# Run the application
ENTRYPOINT exec java ${JAVA_OPTS} -jar app.jar
