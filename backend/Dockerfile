# syntax=docker/dockerfile:1.7
# ---------- build: Maven on Java 25 ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src

# Optional corporate mirror (e.g. Artifactory) for networks that block Maven Central
ARG MAVEN_MIRROR_URL=""
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
      printf '<settings><mirrors><mirror><id>corp</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR_URL" > /opt/settings.xml; \
    else \
      printf '<settings/>' > /opt/settings.xml; \
    fi

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -s /opt/settings.xml dependency:go-offline

COPY src src
# Runs the unit tests; database tests (tag "db") run via compose.test.yml
RUN --mount=type=cache,target=/root/.m2 mvn -B -s /opt/settings.xml package \
 && java -Djarmode=tools -jar target/app.jar extract --layers --destination /extracted

# ---------- runtime: JRE 25 ----------
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=10 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
