FROM docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY settings.xml ./settings.xml
COPY pom.xml ./pom.xml
RUN mvn -s settings.xml -B dependency:go-offline
COPY src ./src
RUN mvn -s settings.xml -B package -DskipTests

FROM docker.m.daocloud.io/library/eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*jar-with-dependencies.jar ./app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
