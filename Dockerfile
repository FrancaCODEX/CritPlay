FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data/uploads && chown -R 10001:10001 /app
COPY --from=build /build/target/critplay-0.5.2.jar /app/critplay.jar
USER 10001:10001
ENV SERVER_ADDRESS=0.0.0.0 PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/critplay.jar"]
