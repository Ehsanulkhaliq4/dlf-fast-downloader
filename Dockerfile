# # Build Stage
# FROM eclipse-temurin:21-jdk AS build
# WORKDIR /workspace
#
# COPY pom.xml .
# COPY mvnw .
# COPY mvnw.cmd .
# COPY .mvn .mvn
# COPY src src
#
# RUN chmod +x mvnw
# RUN ./mvnw clean package -DskipTests
#
# # Runtime Stage
# FROM eclipse-temurin:21-jdk
# WORKDIR /app
#
# # Copy Quarkus App Layout
# COPY --from=build /workspace/target/quarkus-app/ ./
#
# EXPOSE 8080
#
# CMD ["java", "-jar", "quarkus-run.jar"]

#-----------------------------------------------------------------------------------------
# Build Stage
# FROM eclipse-temurin:21-jdk AS build
# WORKDIR /workspace
#
# COPY pom.xml .
# COPY mvnw .
# COPY mvnw.cmd .
# COPY .mvn .mvn
# COPY src src
#
# RUN chmod +x mvnw
# RUN ./mvnw clean package -DskipTests
#
# # Runtime Stage
# FROM eclipse-temurin:21-jdk
# WORKDIR /app
#
# # Install yt-dlp + ffmpeg (needed for conversions)
# RUN apt-get update && \
#     apt-get install -y ffmpeg curl && \
#     curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && \
#     chmod +x /usr/local/bin/yt-dlp
#
# # Copy Quarkus App Layout
# COPY --from=build /workspace/target/quarkus-app/ ./
#
# EXPOSE 8080
#
# CMD ["java", "-jar", "quarkus-run.jar"]
#
# # Install python3 and pip
# RUN apt-get update && \
#     apt-get install -y python3 python3-pip && \
#     rm -rf /var/lib/apt/lists/*
#
# # Install yt-dlp
# RUN pip3 install yt-dlp

#----------------------------------------------------------------

# Build Stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy Maven wrapper and source
COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn
COPY src src

RUN chmod +x mvnw

# Install yt-dlp in build stage for testing if needed
RUN apt-get update && \
    apt-get install -y curl && \
    curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && \
    chmod +x /usr/local/bin/yt-dlp

# Build with retry mechanism for network issues
RUN ./mvnw dependency:go-offline -B || true && \
    ./mvnw clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Install yt-dlp + ffmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg curl && \
    curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && \
    chmod +x /usr/local/bin/yt-dlp

# Copy Quarkus App Layout
COPY --from=build /workspace/target/quarkus-app/ ./

EXPOSE 8080

CMD ["java", "-jar", "quarkus-run.jar"]