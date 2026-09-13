FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline
COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10002 --create-home workstation-gateway
COPY --from=builder --chown=workstation-gateway:workstation-gateway /build/target/*.jar /app/app.jar
USER workstation-gateway
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
