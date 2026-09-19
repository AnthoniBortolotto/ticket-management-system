# syntax=docker/dockerfile:1

# ---- build ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

# Copia so o que descreve dependencias primeiro: assim a camada de download do Maven
# sobrevive a qualquer mudanca de codigo.
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY backend/src/ src/
# Os testes exigem Docker (Testcontainers); quem roda a suite e o CI, nao a imagem.
RUN ./mvnw -B -q clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Nada de `apk add`: o busybox do Alpine ja traz o wget que o healthcheck usa, e
# instalar pacote no build so adiciona uma dependencia de rede que pode falhar.
RUN addgroup -S app && adduser -S -G app app

COPY --from=build /build/target/*.jar app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
