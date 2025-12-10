# Use Eclipse Temurin JDK (Debian-based for glibc compatibility)
FROM eclipse-temurin:21-jdk AS build

# Set working directory
WORKDIR /app

# Copy Maven files
COPY pom.xml .

# Copy source code
COPY src ./src

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage - use JRE for smaller image
FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy the uber JAR from build stage
COPY --from=build /app/target/gcs-storage-metrics-1.0.0.jar app.jar

# Set JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT exec java ${JAVA_OPTS} -jar app.jar
