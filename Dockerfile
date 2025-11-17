FROM maven:3.9.10-openjdk-17 AS build
COPY . .
RUN mvn clean package -DskipTests


FROM openjdk:17.0.1-jdk-slim
COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java","-jar","demo.jar"]
