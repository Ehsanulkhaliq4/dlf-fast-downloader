# ====== Stage 1: Build the application ======
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy Maven files first
COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn

# Pre-fetch dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code AFTER dependencies are downloaded
COPY src src

# Build app (skip tests)
RUN ./mvnw -DskipTests -Dquarkus.package.jar.type=uber-jar package


# ====== Stage 2: Run the application ======
FROM eclipse-temurin:21-jdk

WORKDIR /app
COPY --from=build /workspace/target/*-runner.jar app.jar

ENV PORT=8080
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
