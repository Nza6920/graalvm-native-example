# Spring Boot on GraalVM Native Image

A hands-on GraalVM Native Image course using JDK 25, Spring Boot 4, and Gradle, covering AOT, RuntimeHints, native testing, build diagnostics, and JVM vs. native performance.

> The course lessons and learning notes are written in Chinese.

## What this repository demonstrates

- Building a Spring Boot application as a JVM JAR, JVM OCI image, native executable, and native OCI image
- Understanding the roles of Spring AOT and GraalVM Native Image AOT
- Testing native executables with `nativeTest`
- Reproducing and fixing closed-world reachability failures
- Registering minimal reflection metadata with `RuntimeHintsRegistrar`
- Comparing startup time, memory usage, executable size, and build cost

## Technology baseline

| Tool | Version |
| --- | --- |
| Java / GraalVM | GraalVM CE JDK 25.0.2 |
| Spring Boot | 4.1.0 |
| Spring Framework | 7.0.8 |
| Gradle Wrapper | 9.5.1 |
| GraalVM Native Build Tools | 1.1.1 |

## Project structure

```text
.
├── native-demo/       # Spring Boot sample application
├── lessons/           # Offline interactive course pages
├── learning-records/  # Verified learning outcomes and experiments
├── reference/         # Printable cheat sheets
├── assets/            # Shared lesson styles and quiz behavior
├── MISSION.md         # Learning objective and constraints
├── NOTES.md           # Course progress and next lesson
└── RESOURCES.md       # Primary documentation and communities
```

The Java code is organized by feature:

```text
com.example.nativedemo
├── NativeDemoApplication
└── greeting
    ├── GreetingController
    └── reflective
        ├── GreetingPlugin
        ├── FriendlyGreetingPlugin
        ├── ReflectiveGreetingService
        ├── ReflectiveGreetingController
        └── GreetingRuntimeHints
```

Keeping the reflective implementation and its reachability metadata in the same package makes the native-specific behavior easier to locate and maintain.

## Prerequisites

- A supported native build environment; this course was verified on Linux x86-64
- GraalVM JDK 25 with `native-image`
- GCC and standard native build dependencies
- Docker, only for `bootBuildImage`

With SDKMAN, select an installed GraalVM JDK 25 distribution:

```bash
sdk list java
sdk use java 25.0.2-graalce
java -version
native-image --version
```

## Quick start

```bash
cd native-demo

# Fast JVM feedback
./gradlew test

# Build a local native executable
./gradlew nativeCompile

# Run it
./build/native/nativeCompile/native-demo

# In another terminal
curl http://127.0.0.1:8080/hello
curl http://127.0.0.1:8080/reflective-hello
```

Expected responses:

```text
Hello from GraalVM!
Hello from a reflective plugin!
```

## Build commands

| Command | Output or purpose |
| --- | --- |
| `./gradlew test` | Run JVM tests |
| `./gradlew bootJar` | Build an executable Spring Boot JAR |
| `./gradlew nativeCompile` | Build a local native executable |
| `./gradlew nativeRun` | Build and run the native executable |
| `./gradlew nativeTest` | Build and run the native test executable |
| `./gradlew bootBuildImage` | Build a native OCI image with Buildpacks |
| `./gradlew -PjvmImage bootBuildImage` | Build a JVM OCI image |

`nativeTest` is not a standard Gradle `Test` task and does not accept the usual `--tests` filter.

## RuntimeHints example

The reflective greeting plugin is selected through an application property:

```properties
demo.greeting-plugin=com.example.nativedemo.greeting.reflective.FriendlyGreetingPlugin
```

Because the concrete class name is only known through configuration, Native Image cannot infer the reflective calls. The project registers only the required constructor and method:

```java
hints.reflection().registerType(FriendlyGreetingPlugin.class, type -> type
        .withConstructor(List.of(), ExecutableMode.INVOKE)
        .withMethod("message", List.of(), ExecutableMode.INVOKE));
```

See the complete implementation in [`GreetingRuntimeHints.java`](native-demo/src/main/java/com/example/nativedemo/greeting/reflective/GreetingRuntimeHints.java).

## Course

1. [JDK 25 and the native version triangle](lessons/0001-jdk25-version-triangle.html)
2. [Local `nativeCompile` and `nativeTest`](lessons/0002-local-native-compile-and-native-test.html)
3. [Fix reflection reachability with `RuntimeHints`](lessons/0003-runtime-hints-reflection.html)

References:

- [Toolchain baseline](reference/toolchain-baseline.html)
- [RuntimeHints cheat sheet](reference/runtime-hints-cheatsheet.html)
- [Course progress](NOTES.md)
- [Primary resources](RESOURCES.md)

The HTML lessons are self-contained offline pages. Open them directly in a browser.

## Measured native-build cost

Measurements from this machine are examples, not universal benchmarks:

- Native test image build: approximately 5–6 minutes
- Peak build RSS: approximately 5.1–5.2 GB
- Native test executable: approximately 97 MB
- Native test execution: well under one second after compilation

Most of the time is spent on local reachability analysis and machine-code compilation, not downloading dependencies.

## Buildpack proxy

`native-demo/build.gradle` currently passes this proxy to the Buildpack builder:

```text
http://host.docker.internal:7897
```

Change or remove it if the host proxy is not available. `host.docker.internal` lets a build container address a service running on the Docker host.

## Further reading

- [Spring Boot: GraalVM Native Images](https://docs.spring.io/spring-boot/4.1/reference/packaging/native-image/introducing-graalvm-native-images.html)
- [Spring Framework: Ahead of Time Optimizations](https://docs.spring.io/spring-framework/reference/core/aot.html)
- [GraalVM JDK 25 Native Image](https://www.graalvm.org/jdk25/reference-manual/native-image/)
- [GraalVM Native Build Tools for Gradle](https://graalvm.github.io/native-build-tools/latest/end-to-end-gradle-guide.html)
