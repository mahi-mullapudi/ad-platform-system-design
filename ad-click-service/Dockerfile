# ── Stage 1: Build ──
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy parent POM and ALL module POMs (Maven reactor needs them all)
COPY pom.xml ./pom.xml
COPY ads-domain/pom.xml ./ads-domain/pom.xml
COPY ad-service/pom.xml ./ad-service/pom.xml
COPY ad-click-service/pom.xml ./ad-click-service/pom.xml
COPY ad-click-processor/pom.xml ./ad-click-processor/pom.xml
COPY analytics-gateway/pom.xml ./analytics-gateway/pom.xml

# Copy Maven wrapper
COPY mvnw ./mvnw
COPY .mvn ./.mvn
RUN chmod +x mvnw

# Download dependencies (cached unless POMs change)
RUN ./mvnw dependency:go-offline -pl ad-click-service -am -DskipTests -q || true

# Copy source code
COPY ads-domain/src ./ads-domain/src
COPY ad-click-service/src ./ad-click-service/src

# Build
RUN ./mvnw clean package -pl ad-click-service -am -DskipTests -q

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build /workspace/ad-click-service/target/ad-click-service-*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
