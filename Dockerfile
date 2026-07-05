# ==========================================
# STAGE 1: BUILD THE PROJECT
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# The Tutor's Pro-Move: Copy ONLY the pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Now copy the source code and build it
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# STAGE 2: RUN THE PROJECT
# ==========================================
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built JAR file from Stage 1 (renaming it to app.jar for simplicity)
COPY --from=build /app/target/sb-ecom-0.0.1-SNAPSHOT.jar app.jar

# Expose port 8080
EXPOSE 8080

# Specify the command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]