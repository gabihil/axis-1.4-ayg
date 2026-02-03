# Apache Axis (Fork) Project Information

This `GEMINI.md` provides a summary of the project, build instructions, and development conventions to aid in future interactions.

## Project Overview

This repository is a maintained fork of **Apache Axis 1.4**, focusing on core modules, with additional security patches and compatibility fixes. It is designed to compile and run on **JDK 17 and JDK 25**. The project provides limited functionality compared to the original Axis 1.4, specifically excluding JWS-based services, the Axis admin web application, and legacy samples/optional extras.

## Building and Running

This project uses Apache Maven for its build process.

**Prerequisites:**
*   **Java Development Kit (JDK):** Version 17 or 25. Ensure your `JAVA_HOME` environment variable points to a compatible JDK installation.
*   **Apache Maven:** Version 3.0.4 or higher.

**Build Instructions:**

1.  **Install core modules to your local Maven repository:**
    These commands install `axis-jaxrpc`, `axis-model`, and `axis-tools` into your local `~/.m2` directory, resolving dependencies in the specified order.

    ```bash
    mvn -pl axis-jaxrpc -am -DskipTests install
    mvn -pl axis-model -am -DskipTests install
    mvn -pl axis-tools -am -DskipTests install
    ```

2.  **Build the shaded runtime JAR (for embedding in other projects):**
    This command produces a single JAR file (`axis-rt-core-1.4.1-SNAPSHOT-all.jar`) containing the core Axis runtime.

    ```bash
    mvn -pl axis-rt-core -am -DskipTests package
    ```
    The output JAR will be located at `axis-rt-core/target/axis-rt-core-1.4.1-SNAPSHOT-all.jar`.

**Using `wsdl2java`:**

A helper script for the `wsdl2java` tool is included and can be executed from the repository root after the core modules have been installed:

```bash
./bin/wsdl2java <WSDL-URI> [options]
```

**Targeting specific Java bytecode version:**

By default, the project builds with Java 17 bytecode. To target Java 25 bytecode, run Maven with the following property:

```bash
mvn -DskipTests -Dmaven.compiler.release=25 <your-goal>
```
For a permanent change, you can modify the `<maven.compiler.release>` property in the root `pom.xml`.

## Development Conventions

*   **Build System:** Apache Maven is the primary build tool.
*   **Java Versions:** Development and compilation are targeted for JDK 17 and 25.
*   **JWS Support:** JWS-based deployment and services are explicitly *not supported* in this fork. Ensure you remove any JWS references from `service-config.wsdd`.
*   **Security:** Refer to `docs/security-hardening.md` for additional guidance on security best practices within this project.
*   **Testing:** Tests are skipped by default in the provided build commands (`-DskipTests`). When running tests, be aware of system properties for SAAJ API implementations as configured in the `pom.xml` for `maven-surefire-plugin` and `maven-failsafe-plugin`.
