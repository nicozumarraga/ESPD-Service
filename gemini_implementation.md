# ESPD Service Integration and LLM Automation Strategy

This document outlines a comprehensive strategy for integrating the logic from the existing Java-based ESPD-Service into a modern SaaS application built with a Python backend and a React frontend. Furthermore, it details how to leverage Large Language Models (LLMs) to automate and enhance the ESPD workflow.

## 1. Core Challenge & Strategic Goal

The primary challenge is to utilize the battle-tested, compliant, and complex XML processing logic of the Java ESPD-Service within a completely different technology stack (Python/React). A simple port of the codebase is impractical and risky, given the nuances of the ESPD specification.

**Our strategic goal is to:**
1.  **Preserve** the existing Java service's core logic for XML parsing, validation, and generation.
2.  **Build** a modern, dynamic, and user-friendly frontend in React.
3.  **Orchestrate** the entire process from a Python backend.
4.  **Inject** LLM-powered automation to assist users in completing the ESPD, dramatically improving the user experience.

## 2. Proposed Architecture: A Headless Microservice Approach

The most robust and maintainable architecture is to treat the existing Java application as a **headless microservice**. We will decouple its logic from its current JSP-based view layer and expose it via a RESTful API. This avoids a risky rewrite and encapsulates the legacy component cleanly.

### High-Level Architectural Diagram:

```
+------------------+      (1) JSON      +---------------------+      (2) REST Call      +-----------------------+
|                  | <----------------> |                     | ----------------------> |                       |
|  React Frontend  |      (Wizard UI)   |   Python Backend    |      (XML/JSON)         |  Java ESPD Service    |
|                  |                    |  (Orchestrator)     | <---------------------- |   (Headless)          |
+------------------+                    +----------+----------+      (JSON/XML)         +-----------------------+
                                                   |
                                                   | (3) Prompt/Response
                                                   |
                                          +--------v--------+
                                          |                 |
                                          |   LLM Service   |
                                          |   (e.g., Gemini)|
                                          +-----------------+
```

### Component Responsibilities:

*   **Java ESPD Service (Headless):** Its sole responsibility is to handle the ESPD data model. It will not serve any UI.
    *   **Receives:** An ESPD XML file (request or response).
    *   **Exposes:** A JSON representation of the `EspdDocument`.
    *   **Receives:** An updated JSON `EspdDocument`.
    *   **Exposes:** A valid, compliant ESPD XML output file.

*   **Python Backend (Orchestrator):** This is the central nervous system of the application.
    *   Manages user authentication and sessions.
    *   Communicates with the React frontend.
    *   Calls the Java ESPD service to import/export XML.
    *   Constructs prompts and communicates with the LLM to get automated field suggestions.
    *   Merges LLM suggestions into the ESPD JSON data structure before sending it to the frontend.

*   **React Frontend:** A fully dynamic, client-side application.
    *   Receives the `EspdDocument` as a JSON object from the Python backend.
    *   Dynamically renders the wizard-like interface based on the structure of the JSON (criteria, requirements, etc.).
    *   Manages user input and updates the JSON state locally.
    *   Presents LLM-generated suggestions for user review and approval.
    *   Sends the final, user-approved JSON back to the Python backend.

*   **LLM Service:** A third-party API (e.g., Google's Gemini API).
    *   Receives prompts from the Python backend containing the context of the ESPD and specific fields to be filled.
    *   Returns structured data (e.g., JSON) with suggested text for the fields.

## 3. Implementation Plan

### Step 1: "Headless-ify" and Containerize the Java Service

The first and most critical step is to adapt the Java service.

1.  **Create a new REST Controller:** Add a new controller, e.g., `ApiEspdController.java`, to the `espd-web` module. This controller will handle JSON requests and responses, not JSP views.
2.  **Add JSON Dependency:** Add `com.fasterxml.jackson.core:jackson-databind` to the `espd-web/pom.xml` to enable robust Java-to-JSON serialization.
3.  **Expose Core Logic via REST Endpoints:**
    *   `POST /api/import`: This endpoint will accept a `multipart/form-data` file upload. It will use the existing `EspdXmlImporter` to parse the file and will return a JSON representation of the `EspdDocument` object.
    *   `POST /api/export`: This endpoint will accept a JSON representation of the `EspdDocument` in its request body. It will use the existing `EspdXmlExporter` to generate the final XML and return it as an `application/xml` file.
4.  **JAXB Configuration:** The JAXB configuration is defined in the `JaxbConfiguration.java` file. It is crucial to preserve this configuration in the headless microservice to ensure that the XML is correctly marshalled and unmarshalled. The `Jaxb2Marshaller` is configured to scan the packages containing the `ESPDRequestType` and `ESPDResponseType` classes, so it is important to preserve the package structure of these classes.

5.  **Containerize the Service:** Create a `Dockerfile` to build and run the Java application as a self-contained service. This will simplify deployment and ensure consistency across environments.

    ```dockerfile
    # Use an appropriate base image with Java 8 and Maven
    FROM maven:3.6-jdk-8

    # Copy the source code
    WORKDIR /usr/src/app
    COPY . .

    # Build the project and the WAR file
    RUN mvn clean package

    # Use a Tomcat base image to run the WAR
    FROM tomcat:8.5-jre8
    COPY --from=0 /usr/src/app/espd-web/target/espd-web.war /usr/local/tomcat/webapps/

    EXPOSE 8080
    CMD ["catalina.sh", "run"]
    ```

### Step 2: Develop the Python Backend Orchestrator

1.  **Choose a Framework:** Use a robust Python framework like **FastAPI** or **Django**. FastAPI is highly recommended for its performance and automatic API documentation.
2.  **Create API Endpoints:**
    *   `/espd/upload`: Receives the XML file from the React client. It then calls the Java service's `/api/import` endpoint.
    *   `/espd/get/{espd_id}`: Retrieves the ESPD data (as JSON) for the frontend. This is where the LLM integration happens.
    *   `/espd/update/{espd_id}`: Receives the updated JSON from the React client and saves it.
    *   `/espd/download/{espd_id}`: Retrieves the final JSON, calls the Java service's `/api/export` endpoint, and streams the resulting XML back to the user.
3.  **Data Models:** Use Pydantic to create Python data models that mirror the structure of the `EspdDocument` JSON. This provides validation and type safety. The `EspdDocument.java` class has a flat structure with direct references to each criterion, which should be replicated in the Pydantic model. For example:

    ```python
    from pydantic import BaseModel
    from typing import Optional, List

    class Criterion(BaseModel):
        exists: bool
        # ... other common criterion fields

    class CriminalConvictionsCriterion(Criterion):
        # ... specific fields for this criterion

    class EspdDocument(BaseModel):
        authority: Optional[dict]
        economic_operator: Optional[dict]
        criminal_convictions: Optional[CriminalConvictionsCriterion]
        # ... other criteria
    ```

    When creating a new ESPD from scratch, the Python backend will need to replicate the logic from the `giveLifeToAllExclusionCriteria` and `giveLifeToAllSelectionCriteria` methods in `EspdDocument.java` to dynamically instantiate the criterion objects.

### Step 3: Build the Dynamic React Frontend

1.  **Component-Based Architecture:** Break down the ESPD form into reusable React components. The structure of the `EspdDocument` JSON will guide your component hierarchy.
    *   `<Wizard />`: The main container.
    *   `<ProcedureSection data={espd.procedure} />`
    *   `<ExclusionCriteria data={espd.exclusionCriteria} />`
    *   `<Criterion data={criterion} />`: A generic component to render a single criterion, which can contain various types of requirements (text, boolean, date). The rendering of this component will be driven by a set of props that are similar to the attributes used in the `formTemplate.jsp` file.
    *   `<EcertisInfo />`: A component to display e-Certis information, similar to the `ecertisinfo.jsp` file.

    A component map can be used to dynamically render the different form components based on the type of criterion being rendered.
2.  **State Management:** Use a state management library like **Redux Toolkit** or **Zustand** to manage the `EspdDocument` JSON object. This provides a central, predictable store for the application's state.
3.  **Dynamic Rendering:** Use `map` functions to iterate over lists of criteria and requirements in the JSON to dynamically generate the form fields. Conditional rendering will be used to show/hide elements based on user answers, just like in the original JSP implementation. The `DynamicRequirementGroup` class is a key component for handling dynamic, user-defined requirements. It is essentially a wrapper around a `Map<String, Object>`, which allows it to store an arbitrary number of key-value pairs. The React frontend will need to be able to render a form based on the keys and values in the JSON object, which will require a more flexible and data-driven approach to form generation.

### Step 4: Integrate the LLM for Automation

This is where the "magic" happens.

1.  **Triggering the LLM:** The LLM process should be triggered in the Python backend within the `/espd/get/{espd_id}` endpoint, after the initial `EspdDocument` JSON is fetched from the Java service.
2.  **Intelligent Prompting:** Do not send the entire JSON to the LLM. Instead, create targeted prompts.
    *   **Context:** Provide the LLM with context about the user's company (if available in your SaaS platform) and the purpose of the ESPD.
    *   **Targeted Questions:** For each criterion that requires a text response, create a specific prompt.
    *   **Example Prompt:**
        ```
        You are an expert assistant for public procurement. Your task is to help a company named 'Innovate Inc.' complete a European Single Procurement Document (ESPD).

        Based on the following criterion, please provide a concise and professional response.

        Criterion Title: "Measures to ensure reliability"
        Criterion Description: "Please describe the technical and organizational measures the company has in place to ensure the reliability of its services."

        Company Context: 'Innovate Inc.' is a software development company specializing in cloud-based logistics solutions. They are ISO 27001 certified.

        Generate a JSON response in the following format:
        {
          "criterionId": "CRITERION-XYZ-123",
          "suggestedResponse": "..."
        }
        ```
3.  **Merging Suggestions:** The Python backend will receive the LLM's JSON response. It will then merge these `suggestedResponse` values into the main `EspdDocument` JSON, perhaps under a special `suggestion` key for each field.
4.  **Frontend Display:** The React frontend will check for this `suggestion` key. If it exists, it can display the suggestion directly in the form field, perhaps with a different background color and "Accept" / "Reject" buttons, giving the user full control.

## 4. Conclusion

By adopting this headless microservice architecture, you can successfully bridge the gap between a legacy Java/XML system and a modern Python/React application. This approach is **low-risk**, as it preserves the integrity of the compliant ESPD logic. It is **scalable**, as each component can be developed, deployed, and scaled independently. Finally, it is **innovative**, transforming a tedious documentation process into a streamlined, AI-assisted workflow that provides immense value to your users.
