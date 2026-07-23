<p>
  <a href="#english">English</a> ·
  <a href="#中文">中文</a>
</p>

<a id="english"></a>

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

---

<a id="中文"></a>

# 在 GraalVM Native Image 上运行 Spring Boot

这是一个基于 JDK 25、Spring Boot 4 和 Gradle 的 GraalVM Native Image 实战课程，涵盖 AOT、RuntimeHints、原生测试、构建诊断以及 JVM 与 Native 性能对比。

> 课程页面和学习记录使用中文编写。

## 这个仓库展示了什么

- 把 Spring Boot 应用构建为 JVM JAR、JVM OCI 镜像、本地原生可执行文件和 Native OCI 镜像
- 理解 Spring AOT 与 GraalVM Native Image AOT 各自负责什么
- 使用 `nativeTest` 测试原生可执行程序
- 稳定复现并修复闭世界可达性问题
- 使用 `RuntimeHintsRegistrar` 注册最小范围的反射元数据
- 对比启动时间、内存占用、可执行文件大小和构建成本

## 技术基线

| 工具 | 版本 |
| --- | --- |
| Java / GraalVM | GraalVM CE JDK 25.0.2 |
| Spring Boot | 4.1.0 |
| Spring Framework | 7.0.8 |
| Gradle Wrapper | 9.5.1 |
| GraalVM Native Build Tools | 1.1.1 |

## 项目结构

```text
.
├── native-demo/       # Spring Boot 示例应用
├── lessons/           # 可离线打开的交互式课程
├── learning-records/  # 已验证的学习成果与实验记录
├── reference/         # 适合打印的速查表
├── assets/            # 课程共享样式与测验脚本
├── MISSION.md         # 学习目标与约束
├── NOTES.md           # 课程进度与下一课
└── RESOURCES.md       # 官方资料与社区
```

Java 代码按功能分包：

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

反射实现和对应的可达性元数据放在同一个包中，让 Native 专属行为更容易定位和维护。

## 环境要求

- Native Image 支持的本机构建环境；本课程已在 Linux x86-64 上验证
- 带有 `native-image` 的 GraalVM JDK 25
- GCC 和标准原生构建依赖
- Docker，仅在执行 `bootBuildImage` 时需要

使用 SDKMAN 选择已经安装的 GraalVM JDK 25：

```bash
sdk list java
sdk use java 25.0.2-graalce
java -version
native-image --version
```

## 快速开始

```bash
cd native-demo

# 快速 JVM 反馈
./gradlew test

# 构建本地原生可执行文件
./gradlew nativeCompile

# 运行
./build/native/nativeCompile/native-demo

# 在另一个终端验证
curl http://127.0.0.1:8080/hello
curl http://127.0.0.1:8080/reflective-hello
```

预期响应：

```text
Hello from GraalVM!
Hello from a reflective plugin!
```

## 构建命令

| 命令 | 输出或用途 |
| --- | --- |
| `./gradlew test` | 运行 JVM 测试 |
| `./gradlew bootJar` | 构建可执行 Spring Boot JAR |
| `./gradlew nativeCompile` | 构建本地原生可执行文件 |
| `./gradlew nativeRun` | 构建并运行原生可执行文件 |
| `./gradlew nativeTest` | 构建并运行原生测试程序 |
| `./gradlew bootBuildImage` | 使用 Buildpacks 构建 Native OCI 镜像 |
| `./gradlew -PjvmImage bootBuildImage` | 构建 JVM OCI 镜像 |

`nativeTest` 不是标准 Gradle `Test` 任务，因此不支持常规的 `--tests` 过滤器。

## RuntimeHints 示例

反射问候插件通过应用配置选择：

```properties
demo.greeting-plugin=com.example.nativedemo.greeting.reflective.FriendlyGreetingPlugin
```

具体实现类只通过配置字符串出现，因此 Native Image 无法自动推断反射调用。项目只注册实际需要的构造器和方法：

```java
hints.reflection().registerType(FriendlyGreetingPlugin.class, type -> type
        .withConstructor(List.of(), ExecutableMode.INVOKE)
        .withMethod("message", List.of(), ExecutableMode.INVOKE));
```

完整实现见 [`GreetingRuntimeHints.java`](native-demo/src/main/java/com/example/nativedemo/greeting/reflective/GreetingRuntimeHints.java)。

## 课程

1. [JDK 25 与 Native 版本三角](lessons/0001-jdk25-version-triangle.html)
2. [本地 `nativeCompile` 与 `nativeTest`](lessons/0002-local-native-compile-and-native-test.html)
3. [使用 `RuntimeHints` 修复反射可达性](lessons/0003-runtime-hints-reflection.html)

参考资料：

- [工具链基线](reference/toolchain-baseline.html)
- [RuntimeHints 速查表](reference/runtime-hints-cheatsheet.html)
- [课程进度](NOTES.md)
- [主要资料](RESOURCES.md)

HTML 课程页面可以离线使用，直接在浏览器中打开即可。

## 实测 Native 构建成本

以下数据来自当前机器，仅作为示例，不是通用基准：

- 原生测试镜像构建：约 5–6 分钟
- 构建峰值 RSS：约 5.1–5.2 GB
- 原生测试可执行文件：约 97 MB
- 编译完成后，原生测试执行时间远低于 1 秒

大部分时间用于本机可达性分析和机器码编译，而不是下载依赖。

## Buildpack 代理

`native-demo/build.gradle` 当前会把以下代理传入 Buildpack builder：

```text
http://host.docker.internal:7897
```

如果宿主机代理不可用，请修改或删除该配置。`host.docker.internal` 用于让构建容器访问 Docker 宿主机上运行的服务。

## 延伸阅读

- [Spring Boot：GraalVM Native Images](https://docs.spring.io/spring-boot/4.1/reference/packaging/native-image/introducing-graalvm-native-images.html)
- [Spring Framework：Ahead of Time Optimizations](https://docs.spring.io/spring-framework/reference/core/aot.html)
- [GraalVM JDK 25 Native Image](https://www.graalvm.org/jdk25/reference-manual/native-image/)
- [GraalVM Native Build Tools for Gradle](https://graalvm.github.io/native-build-tools/latest/end-to-end-gradle-guide.html)
