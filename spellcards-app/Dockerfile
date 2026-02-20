FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY spellcards-api/pom.xml spellcards-api/pom.xml
COPY spellcards-app/pom.xml spellcards-app/pom.xml
COPY spellcards-web/pom.xml spellcards-web/pom.xml
COPY scripts scripts
COPY spellcards-api spellcards-api
COPY spellcards-app spellcards-app

RUN mvn -B -pl spellcards-app -am -Pfetch-foundry-data -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/spellcards-app/target/spellcards-app-*.jar /app/spellcards-app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/spellcards-app.jar"]
