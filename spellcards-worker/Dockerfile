FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY spellcards-api/pom.xml spellcards-api/pom.xml
COPY spellcards-queue/pom.xml spellcards-queue/pom.xml
COPY spellcards-app/pom.xml spellcards-app/pom.xml
COPY spellcards-worker/pom.xml spellcards-worker/pom.xml
COPY spellcards-web/pom.xml spellcards-web/pom.xml
COPY scripts scripts
COPY spellcards-api spellcards-api
COPY spellcards-queue spellcards-queue
COPY spellcards-app spellcards-app
COPY spellcards-worker spellcards-worker

RUN mvn -B -pl spellcards-worker -am -Pfetch-foundry-data -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/spellcards-worker/target/spellcards-worker-*.jar /app/spellcards-worker.jar

ENTRYPOINT ["java", "-jar", "/app/spellcards-worker.jar"]
