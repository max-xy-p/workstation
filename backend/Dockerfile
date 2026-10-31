FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

# 在切换普通用户前准备执行器日志目录，新建日志卷会继承目录内容与权限。
RUN useradd --system --uid 10001 --create-home workstation \
    && mkdir -p /data/applogs/xxl-job/jobhandler \
    && chown -R workstation:workstation /data/applogs

COPY --from=builder --chown=workstation:workstation /build/target/*.jar /app/app.jar

USER workstation

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
