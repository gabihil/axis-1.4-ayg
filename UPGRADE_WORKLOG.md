# Upgrade Worklog (deps-only branch)

Last updated: 2026-02-02
Branch: `upgrade/deps-only`

## Done

Commits already pushed:

- `3ff1f8f70` Bump commons-lang to commons-lang3
- `bddacf460` Modernize axis-rt-core runtime deps
- `ceda22ce2` Switch xmlunit to xmlunit-legacy
- `0d9c360b0` Document Java 25 bytecode build option
- `874883770` Upgrade Castor databinding dependency
- `8fc5a6cd1` Upgrade HttpClient transport to HttpClient 5
- `02c2ca58c` Update commons-cli to 1.11.0
- `279901fb5` Update commons-daemon to 1.5.1
- `5dd52be07` Set Java version to 25
- `ff19d5f00` Upgrade maven-enforcer-plugin
- `d71accc66` Upgrade maven-project-info-reports-plugin
- `331e02f30` Upgrade maven-scm-publish-plugin
- `765cd8400` Upgrade Axiom testutils to 2.0.0 (with test fix in `axis-rt-core`)
- `d4f81c4bc` Upgrade maven-jar-plugin to 3.5.0
- `39290de68` Upgrade maven-shade-plugin to 3.6.1
- `b95a4648a` Upgrade maven-dependency-plugin to 3.9.0
- `b7d022826` Upgrade maven-assembly-plugin to 3.8.0

## Validated

Key successful builds/tests run during this session:

- `mvn -pl axis-rt-core -am test`
- `mvn -pl axis-rt-core -am -DskipTests package`
- `mvn -pl axis-model,axis,axis-rt-core -am -DskipTests package`
- `mvn -pl axis-rt-transport-http-javanet -am -DskipTests pre-integration-test`
- `mvn -pl distribution -am -DskipTests package`

## Important notes

- `maven-shade-plugin` 3.5.0 failed with Java 25 class files (`Unsupported class file major version 69`), so it was moved to `3.6.1`.
- `maven-assembly-plugin` 2.2.2 caused `inputFile is null`; upgraded to `3.8.0` and distribution packaging now works.
- There is an untracked local `.m2/` directory in repo root; do not commit it.

## Next (priority order)

1. Upgrade remaining old build plugins one-by-one in root `pom.xml`:
   - `maven-antrun-plugin` 1.7
   - `build-helper-maven-plugin` 1.7
   - `maven-invoker-plugin` 1.7
   - `maven-site-plugin` 3.4
   - `maven-eclipse-plugin` 2.9 (optional/legacy)
2. Then consider safer plugin bumps:
   - `maven-compiler-plugin` 3.11.0 -> newer 3.x
   - `maven-surefire-plugin`/`maven-failsafe-plugin` 3.1.2 -> newer 3.x
   - `maven-war-plugin` 3.4.0 -> newer 3.x
3. Dependency refresh pass (non-Jakarta-only changes first):
   - `reload4j` and `commons-io`
   - review other runtime deps for stable non-milestone upgrades
4. After deps-only is stable, replay/cherry-pick needed commits into `upgrade/jakarta-next`.

