# Build: docker build -f docker/Dockerfile -t admin-api-starter .
# Run:   docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod -e DB_URL=… admin-api-starter

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/admin-api-starter-*.jar extract --layers --launcher --destination /app

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
