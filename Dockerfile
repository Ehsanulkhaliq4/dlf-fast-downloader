# Use Quarkus recommended JDK
FROM eclipse-temurin:21-jdk

# Set working directory
WORKDIR /app

# Copy Maven/Gradle build output to container
COPY target/*-runner.jar app.jar

# Expose Quarkus default port
EXPOSE 8080

# Run the app
CMD ["java", "-jar", "app.jar"]
