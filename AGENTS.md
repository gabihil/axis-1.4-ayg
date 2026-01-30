# Repository Guidelines

## Project Structure & Module Organization
This is a multi-module Maven repo (fork of Apache Axis 1.4) with core runtime work centered in `axis-rt-core/`. Key areas:
- `axis-rt-*/` runtime modules (core, transports, databinding, etc.).
- `axis-jaxrpc/`, `axis-model/`, `axis-tools/` support APIs and tooling.
- `src/` legacy sources; `samples/`, `docs/`, `xmls/` (Checkstyle config), `bin/` helper scripts.
- Tests live under `*/src/test/java/` plus legacy harnesses in `test/` and `tests/`.

## Build, Test, and Development Commands
Use JDK 17+ and Maven for the supported build path.
- `mvn -pl axis-jaxrpc -am -DskipTests install`  install prerequisites for tools.
- `mvn -pl axis-model  -am -DskipTests install`  install the model module.
- `mvn -pl axis-tools  -am -DskipTests install`  install tooling (needed for `wsdl2java`).
- `mvn -pl axis-rt-core -am -DskipTests package`  build the shaded runtime JAR.
- `./bin/wsdl2java <WSDL-URI> [options]`  run the helper script after the above installs.

Legacy Ant targets exist in `build.xml`/`buildTest.xml` (use only if needed): `ant compile`, `ant junit`, `ant functional-tests`, `ant all-tests`.

## Coding Style & Naming Conventions
- Java sources use 4-space indentation and K&R-style braces; follow existing local style in the module you touch.
- Packages use `org.apache.axis.*`; tests are typically `Test*.java` under `*/src/test/java/`.
- Checkstyle configuration lives in `xmls/checkstyle.xml` (Ant target `checkstyle`).

## Testing Guidelines
- Unit tests are JUnit-based (Maven Surefire/Failsafe configured in `pom.xml`).
- Preferred: `mvn -pl <module> -am test` for module-scoped runs.
- Legacy suites run through Ant (`ant junit`, `ant functional-tests`).
- Keep new tests in `*/src/test/java/` and name them `Test<Feature>.java`.

## Commit & Pull Request Guidelines
- Git history favors short, imperative commit messages (e.g., “Update …”, “Fix …”, “Disable …”).
- PRs should be focused, describe the change and rationale, and include test notes (what ran or why skipped). Add security-impact notes when touching `docs/security-hardening.md` or config files like `axis.properties` / `service-config.wsdd`.

## Security & Configuration Notes
- This fork disables JWS-based deployment; remove JWS references from `service-config.wsdd`.
- Review `docs/security-hardening.md` before changing runtime defaults.
