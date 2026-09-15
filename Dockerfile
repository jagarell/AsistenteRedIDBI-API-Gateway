# --- Etapa de build: compila el jar con Maven ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cachear dependencias en su propia capa antes de copiar el código fuente,
# para que un cambio en src/ no vuelva a descargar todo Maven Central.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B -q package -DskipTests

# --- Etapa de runtime: solo el JRE + el jar ya compilado ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Railway asigna el puerto real en la variable PORT; application.yml ya lee
# SERVER_PORT (con 8080 como default) — se la mapeamos al arrancar.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
