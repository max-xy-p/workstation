FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY mail-provider/pom.xml ./pom.xml
RUN mvn --batch-mode dependency:go-offline
COPY mail-provider/src ./src
RUN mvn --batch-mode -DskipTests package

FROM quay.io/keycloak/keycloak:26.7.3
COPY --from=builder --chown=keycloak:keycloak /build/target/workstation-keycloak-mail-provider-*.jar /opt/keycloak/providers/
ENV KC_SPI_EMAIL_SENDER__PROVIDER=workstation-mail
