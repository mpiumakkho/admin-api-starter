# Build: docker build -f docker/Dockerfile -t admin-api-starter .
# Run:   docker run -p 8080:8080 -e DB_URL=… -e AC_ENCRYPTION_PASSWORD=… admin-api-starter   (README, "Config ที่ต้องตั้งใน production")
# The image runs the prod profile itself, so a forgotten -e SPRING_PROFILES_ACTIVE never starts it on development settings

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/admin-api-starter-*.jar extract --layers --launcher --destination /app

FROM eclipse-temurin:25-jre
ENV SPRING_PROFILES_ACTIVE=prod
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
