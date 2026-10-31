# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build

ARG MODULE_DIR=mc-pulsor-server

WORKDIR /src
COPY . /src

# Build Spring Boot jar (boot-maven-plugin repackage). Copy the non-original jar to /app/app.jar
RUN --mount=type=cache,target=/root/.m2 \
    set -eux; \
    mvn -DskipTests -f "${MODULE_DIR}/pom.xml" package; \
    mkdir -p /app; \
    ls -la "${MODULE_DIR}/target"; \
    JAR="$(ls -1 ${MODULE_DIR}/target/*.jar | grep -v 'original-' | head -n 1)"; \
    echo "Using jar: $JAR"; \
    cp "$JAR" /app/app.jar

# ---------- Runtime: STDIO + Inspector (one container) ----------
FROM eclipse-temurin:21-jre-alpine AS stdio
WORKDIR /app
RUN apk add --no-cache nodejs npm
COPY --from=build /app/app.jar /app/app.jar

EXPOSE 6274 6277
CMD sh -c 'npx @modelcontextprotocol/inspector -e MCP_TRANSPORT=$MCP_TRANSPORT -- java -jar /app/app.jar'

# ---------- Runtime: HTTP server only (inspector separate) ----------
FROM eclipse-temurin:21-jre-alpine AS http
WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar

EXPOSE 8091
CMD ["sh", "-c", "java -jar /app/app.jar"]
