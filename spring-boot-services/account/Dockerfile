FROM eclipse-temurin:17-jre
LABEL authors="Daria Prudnikova"

WORKDIR /app/account

COPY target/account-*.jar app.jar
COPY src/main/resources/db/changelog /app/resources/db/changelog

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
