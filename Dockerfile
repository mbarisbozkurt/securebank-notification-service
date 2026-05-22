FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN addgroup --system securebank && adduser --system --ingroup securebank securebank

COPY --from=build /workspace/target/securebank-notification-service-0.0.1-SNAPSHOT.jar app.jar

USER securebank

ENTRYPOINT ["java", "-jar", "app.jar"]
