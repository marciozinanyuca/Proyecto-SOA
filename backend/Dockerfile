# Build infra/docker/oracle-jdk17 first, or supply an approved equivalent by digest.
ARG JAVA_IMAGE=ecoinvest/oracle-jdk:17.0.12
FROM maven:3.9.9-eclipse-temurin-17@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc AS maven-distribution

FROM ${JAVA_IMAGE} AS build
USER 0
COPY --from=maven-distribution /usr/share/maven /opt/maven
ENV MAVEN_HOME=/opt/maven
ENV PATH="/opt/maven/bin:${PATH}"
WORKDIR /build
COPY pom.xml ./
COPY src ./src
# Maven runs on the supplied Oracle JVM, not the Maven distribution stage JVM.
RUN java -version 2>&1 | grep -F '17.0.12' \
    && java -XshowSettings:properties -version 2>&1 | grep -F 'java.vendor = Oracle Corporation' \
    && mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package \
    && cp target/soa-0.0.1-SNAPSHOT.jar /build/application.jar

FROM ${JAVA_IMAGE} AS runtime
USER 0
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/application.jar /app/application.jar
ENV SPRING_PROFILES_ACTIVE=azure
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
