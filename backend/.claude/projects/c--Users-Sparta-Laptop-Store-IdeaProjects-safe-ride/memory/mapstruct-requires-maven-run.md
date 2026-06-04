---
name: mapstruct-requires-maven-run
description: rides-service must be run via Maven, not the VS Code Run button, or the MapStruct mapper bean is missing
metadata:
  type: project
---

Running `rides-service` (and any module using MapStruct) via the **VS Code Run/Debug button** fails at startup with: "Parameter N of constructor in RideService required a bean of type 'RideMapper' that could not be found." The Eclipse JDT compiler VS Code uses does not run the MapStruct annotation processor, so the generated `RideMapperImpl` (`@Component`) is absent from the classpath.

**Why:** MapStruct's processor only runs under Maven's `maven-compiler-plugin` `annotationProcessorPaths` config. Maven correctly generates `target/generated-sources/.../RideMapperImpl.java` and compiles it into `target/classes`.

**How to apply:** Run services with `.\mvnw.cmd spring-boot:run` (use `clean` first to wipe stale JDT output). This is also what CLAUDE.md documents. DevTools fast-restart still works under Maven — recompile in a second terminal with `.\mvnw.cmd compile`.

Related: [[rides-spec-entity-naming]].
