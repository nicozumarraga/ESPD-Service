# Stage 1: Build the application using Maven
FROM maven:3.8.4-openjdk-8-slim AS build

WORKDIR /usr/src/app

# Copy the pom.xml files first to leverage Docker layer caching
COPY pom.xml .
COPY espd-web/pom.xml ./espd-web/
COPY espd-docs/pom.xml ./espd-docs/

# Download dependencies
RUN mvn dependency:go-offline

# Copy the rest of the source code
COPY . .

# Build the application
RUN mvn clean package

# Stage 2: Create the final image using Tomcat
FROM tomcat:8.5-jre8-slim

# Remove the default webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy the built WAR file from the build stage
COPY --from=build /usr/src/app/espd-web/target/espd-web.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]