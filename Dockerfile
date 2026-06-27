# ── Stage 1: BUILD ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: RUNTIME ──────────────────────────────────────────────────────────
# file-service richiede ExifTool per estrarre i metadati dai video.
# ExifTool è uno script Perl: installiamo perl + il pacchetto exiftool da Alpine.
FROM eclipse-temurin:17-jre-alpine AS runtime

# apk è il package manager di Alpine Linux (equivalente di apt su Ubuntu)
# --no-cache evita di salvare la cache dei pacchetti nell'immagine — mantiene il layer piccolo
RUN apk add --no-cache exiftool

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8082

# EXIFTOOL_PATH sovrascrive il valore default "exiftool" in application.yaml.
# Dopo apk add, l'eseguibile si trova in /usr/bin/exiftool.
ENV EXIFTOOL_PATH=/usr/bin/exiftool

ENTRYPOINT ["java", "-jar", "app.jar"]
