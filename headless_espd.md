# Guide: ESPD-Service Headless API - Working Prototype

This document provides a pragmatic, step-by-step guide for quickly creating a **working prototype** of a headless API from the existing `ESPD-Service`. The goal is to have a functional API that can import/export ESPD XML files within 1-2 days, deferring production concerns like testing, security, and optimization for later iterations.

## Prerequisites

*   **Technology Stack:** Java 8, Spring Boot, Maven
*   **Tools:** An IDE (e.g., IntelliJ IDEA, VS Code), Docker Desktop, Postman or curl
*   **Understanding:** Basic Spring MVC and REST principles

## Important Notes

*   This is a **prototype** - focus on making it work first, optimize later
*   The existing codebase uses Spring Boot 1.x patterns - we'll work with what's there
*   Skip unit tests, extensive error handling, and security for now
*   The actual configuration files have slightly different names than you might expect

---

## Step 1: Add Required Dependencies

First, we need to add dependencies for JSON serialization and modern Spring Boot features.

1.  **Open the `pom.xml` file** located at `/espd-web/pom.xml`.
2.  **Add the following dependencies** within the `<dependencies>` section:

```xml
<!-- Jackson for JSON serialization -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<!-- Spring Boot Web Starter (if not already present) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

*Note: The project might already have some of these. Don't worry about version conflicts for the prototype.*

## Step 2: Create the Headless REST Controller

We will create a new controller dedicated to the API. This controller will not be concerned with rendering views (JSPs).

1.  **Create a new Java class** named `ApiEspdController.java` inside the `eu.europa.ec.grow.espd.controller` package.
2.  **Annotate the class** with `@RestController`. This is a specialized version of `@Controller` that includes the `@ResponseBody` annotation, meaning all return values from methods will be automatically serialized into JSON.
3.  **Inject the required services** (`EspdXmlImporter` and `EspdXmlExporter`) using constructor injection.

Here is the initial structure of the file:

```java
package eu.europa.ec.grow.espd.controller;

import eu.europa.ec.grow.espd.domain.EspdDocument;
import eu.europa.ec.grow.espd.util.EspdExporter;
import eu.europa.ec.grow.espd.xml.EspdXmlImporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@RestController
@RequestMapping("/api") // All endpoints in this controller will be prefixed with /api
public class ApiEspdController {

    private final EspdXmlImporter xmlImporter;
    private final EspdExporter espdExporter;

    @Autowired
    public ApiEspdController(EspdXmlImporter xmlImporter, EspdExporter espdExporter) {
        this.xmlImporter = xmlImporter;
        this.espdExporter = espdExporter;
    }

    // Endpoints will be added here in the next steps.
}
```

## Step 3: Implement the `/api/import` Endpoint

This endpoint will receive an ESPD XML file, process it, and return the `EspdDocument` as a JSON object.

1.  **Add the following method** to `ApiEspdController.java`.
2.  This method will handle `POST` requests to `/api/import` and expect a file upload (`multipart/form-data`).
3.  It leverages the existing `EspdXmlImporter.importAmbiguousEspdFile` method, which is perfect for our needs as it can handle both ESPD Request and Response XMLs.

```java
@PostMapping("/import")
public ResponseEntity<EspdDocument> importEspd(@RequestParam("file") MultipartFile file) {
    if (file.isEmpty()) {
        return ResponseEntity.badRequest().build();
    }

    try (InputStream is = file.getInputStream()) {
        return xmlImporter.importAmbiguousEspdFile(is)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    } catch (Exception e) {
        // Log the exception properly in a real implementation
        return ResponseEntity.status(500).build();
    }
}
```

## Step 4: Implement the `/api/export` Endpoint

This endpoint will receive a JSON representation of the `EspdDocument`, convert it back to XML, and return the file for download.

1.  **Add the following method** to `ApiEspdController.java`.
2.  This method handles `POST` requests to `/api/export`.
3.  It uses the `@RequestBody` annotation to deserialize the incoming JSON into our `EspdDocument` object.
4.  It uses the existing `EspdExporter` to generate the XML.
5.  It determines whether to generate a Request or Response based on whether the `economicOperator` field is present in the document.

```java
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.io.ByteArrayOutputStream;

// ... inside ApiEspdController class

@PostMapping("/export")
public ResponseEntity<byte[]> exportEspd(@RequestBody EspdDocument espdDocument) {
    try {
        // Determine if this is a request or response.
        // A response is characterized by the presence of the Economic Operator.
        boolean isResponse = espdDocument.getEconomicOperator() != null;

        ByteArrayOutputStream out = isResponse
                ? espdExporter.generateEspdResponse(espdDocument)
                : espdExporter.generateEspdRequest(espdDocument);

        String fileName = isResponse ? "espd-response.xml" : "espd-request.xml";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok()
                .headers(headers)
                .body(out.toByteArray());

    } catch (Exception e) {
        // Log the exception
        return ResponseEntity.status(500).build();
    }
}
```

## Step 5: Clean Up View-Specific Configurations

The current application is configured to serve JSPs using Apache Tiles. In a true headless service, this is unnecessary overhead.

1.  **Remove Tiles Configuration:** The `TilesConfiguration.java` class can be safely deleted or commented out.
2.  **Remove Wro4j Configuration:** The `Wro4jConfiguration.java` for web resource optimization is also no longer needed and can be removed.
3.  **Review `WebConfig.java`:** Examine `WebConfig.java` for any view-related beans, such as `ViewResolver`s or `Interceptor`s that are specific to the UI workflow, and remove them.
4.  **Session Management:** The `@SessionAttributes("espd")` on `EspdController` is stateful and tied to a user's web session. Our new API is stateless. The `ApiEspdController` does not have this annotation, and the state will be managed by the Python backend, which is the correct approach.

## Step 6: Build and Package

With the changes in place, build the application to ensure everything compiles and packages correctly.

1.  **Open a terminal** in the root directory of the `ESPD-Service` project.
2.  **Run the Maven command:**

```bash
mvn clean package
```

This will produce the `espd-web.war` file in the `espd-web/target/` directory.

## Step 7: Containerize with Docker

Create a `Dockerfile` in the root of the project to containerize the headless service.

```dockerfile
# Stage 1: Build the application using Maven
FROM maven:3.8.4-openjdk-8-slim AS build

WORKDIR /usr/src/app

# Copy the pom.xml files first to leverage Docker layer caching
COPY pom.xml .
COPY espd-web/pom.xml ./espd-web/
COPY espd-docs/pom.xml ./espd-docs/
# Copy other necessary module descriptors if they exist

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

```

**To build the Docker image:**

```bash
docker build -t espd-headless-service:latest .
```

**To run the Docker container:**

```bash
docker run -p 8080:8080 espd-headless-service:latest
```

## Step 8: Validate the Headless Service

Once the service is running, you can test the new endpoints using a tool like `curl` or Postman.

**Test the `/api/import` endpoint:**

*   You will need a sample ESPD XML file (e.g., `sample-request.xml`).

```bash
curl -X POST -F "file=@/path/to/your/sample-request.xml" http://localhost:8080/api/import
```

*   **Expected Result:** A JSON payload representing the `EspdDocument`.

**Test the `/api/export` endpoint:**

1.  Save the JSON output from the import request into a file (e.g., `espd.json`).
2.  Make a `POST` request with this JSON as the body.

```bash
curl -X POST -H "Content-Type: application/json" -d @espd.json http://localhost:8080/api/export -o exported-espd.xml
```

*   **Expected Result:** An `exported-espd.xml` file will be created in your current directory, containing the valid ESPD XML.

---

## Implementation Details and Corrections

### Actual Implementation of ApiEspdController.java

The final working implementation differs slightly from the guide above. Here's the complete, tested code:

```java
package eu.europa.ec.grow.espd.controller;

import com.google.common.base.Optional;
import eu.europa.ec.grow.espd.domain.EspdDocument;
import eu.europa.ec.grow.espd.xml.EspdXmlExporter;
import eu.europa.ec.grow.espd.xml.EspdXmlImporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@RestController
@RequestMapping("/api")
public class ApiEspdController {

    private final EspdXmlImporter xmlImporter;
    private final EspdXmlExporter xmlExporter;

    @Autowired
    public ApiEspdController(EspdXmlImporter xmlImporter, EspdXmlExporter xmlExporter) {
        this.xmlImporter = xmlImporter;
        this.xmlExporter = xmlExporter;
    }

    @PostMapping("/import")
    public ResponseEntity<?> importEspd(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        try (InputStream is = file.getInputStream()) {
            Optional<EspdDocument> espdDocument = xmlImporter.importAmbiguousEspdFile(is);
            if (espdDocument.isPresent()) {
                return ResponseEntity.ok(espdDocument.get());
            } else {
                return ResponseEntity.badRequest().body("Could not parse ESPD file");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }

    @PostMapping("/export")
    public ResponseEntity<?> exportEspd(@RequestBody EspdDocument espdDocument) {
        try {
            boolean isResponse = espdDocument.getEconomicOperator() != null;

            ByteArrayOutputStream out = isResponse
                    ? xmlExporter.generateEspdResponse(espdDocument)
                    : xmlExporter.generateEspdRequest(espdDocument);

            String fileName = isResponse ? "espd-response.xml" : "espd-request.xml";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setContentDispositionFormData("attachment", fileName);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(out.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error generating XML: " + e.getMessage());
        }
    }
}
```

**Key differences from the guide:**
1. Uses `EspdXmlExporter` directly instead of `EspdExporter`
2. Uses Google Guava's `Optional` (which the project already uses)
3. Returns `ResponseEntity<?>` to handle both success and error cases
4. Proper error messages for debugging

### Java 8 Requirement

**Important:** This project requires Java 8. If you're using a newer Java version, you'll encounter compilation errors. For macOS users with Apple Silicon, install Azul Zulu Java 8:

```bash
# Install via Homebrew
brew install --cask zulu8

# Set JAVA_HOME before building or running
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# Verify Java version
java -version  # Should show version 1.8.x
```

### Building the Project

```bash
# With Java 8 properly set
mvn clean package -DskipTests
```

### Running the Application

#### Option 1: Run Locally with Java

```bash
# Set Java 8 environment
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# Run the application
java -Dspring.profiles.active=dev -Dlogging.config= -Xms768m -Xmx768m -jar espd-web/target/espd-web.war
```

The application will start on `http://localhost:8080`

#### Option 2: Run with Docker

```bash
# Build the Docker image
docker build -t espd-headless-service:latest .

# Run the container
docker run -p 8080:8080 espd-headless-service:latest
```

### API Endpoints

The headless API provides two endpoints:

1. **Import ESPD XML to JSON**
   - **URL:** `POST http://localhost:8080/api/import`
   - **Content-Type:** `multipart/form-data`
   - **Parameter:** `file` - The ESPD XML file to import
   - **Response:** JSON representation of the ESPD document

   Example:
   ```bash
   curl -X POST -F "file=@sample-request.xml" http://localhost:8080/api/import
   ```

2. **Export JSON to ESPD XML**
   - **URL:** `POST http://localhost:8080/api/export`
   - **Content-Type:** `application/json`
   - **Body:** JSON representation of the ESPD document
   - **Response:** ESPD XML file (attachment)

   Example:
   ```bash
   curl -X POST -H "Content-Type: application/json" -d @espd.json \
        http://localhost:8080/api/export -o exported-espd.xml
   ```

### Testing the Implementation

1. **Prepare a sample ESPD XML file** (request or response)
2. **Import it** using the `/api/import` endpoint
3. **Save the JSON response** to a file
4. **Export it back to XML** using the `/api/export` endpoint
5. **Compare** the original and exported XML files to verify correctness

### Integration with Python/React Architecture

This headless service is designed to be integrated as described in the `gemini_implementation.md` document:

- **Python Backend** calls these endpoints to handle XML/JSON conversion
- **React Frontend** works with the JSON representation
- **LLM Integration** processes the JSON data for automation

### Troubleshooting

1. **Java Version Issues**: Ensure Java 8 is being used (`java -version`)
2. **Port Conflicts**: Change the port using `-Dserver.port=8081`
3. **Memory Issues**: Adjust `-Xms` and `-Xmx` parameters as needed
4. **Build Failures**: Clear Maven cache with `mvn dependency:purge-local-repository`

---

This guide provides the blueprint for transforming the ESPD-Service. By following these steps, an engineer can successfully create a decoupled, modern, and maintainable microservice ready for integration into the broader SaaS ecosystem.
