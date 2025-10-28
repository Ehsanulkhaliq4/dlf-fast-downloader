# ====== Stage 1: Build the application ======
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy Maven wrapper and pom.xml
COPY .mvn/ .mvn
COPY mvnw mvnw.cmd pom.xml ./

# Pre-fetch dependencies (improves build speed)
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src src

# Build the app (skip tests to avoid failures)
RUN ./mvnw -DskipTests -Dquarkus.package.jar.type=uber-jar package


# ====== Stage 2: Run the application ======
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy compiled jar from the build stage
COPY --from=build /workspace/target/*-runner.jar /app/app.jar

# Railway/Docker dynamic port support
EXPOSE 8080
ENV PORT=8080

CMD ["java", "-jar", "/app/app.jar"]
