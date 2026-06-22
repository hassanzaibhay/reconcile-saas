FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :app:bootJar --no-daemon -x test

RUN java -Djarmode=layertools -jar app/build/libs/app-*.jar extract --destination /extracted

FROM eclipse-temurin:21-jre AS runtime
RUN useradd -r -u 1001 -g root app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER 1001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=container
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
