---
title: "Java 的云原生竞争力：Spring Boot 4、JDK 25 与 GraalVM Native Image 的工程权衡"
date: 2026-07-24
description: "讨论 Spring Boot 4、JDK 25 与 GraalVM Native Image 如何影响 Java 的云原生竞争力，以及选型、AOT 设计、测试、容器交付与性能之间的工程权衡。"
tags:
  - Spring Boot 4
  - JDK 25
  - GraalVM
  - Native Image
  - AOT
---

# Java 的云原生竞争力：Spring Boot 4、JDK 25 与 GraalVM Native Image 的工程权衡

GraalVM Native Image 常以“毫秒级启动”和“更低内存”为卖点。执行 `nativeCompile` 成功只解决了构建问题，项目要稳定使用 Native Image，还要接受一份更严格的构建期契约：

> classpath、Bean 图以及大部分运行时会触达的代码和资源，都应在构建阶段变得可知、可验证、可复现。

Native Image 和普通 JVM 的运行方式不同。JVM 会在程序启动或运行时完成很多工作，Native Image 则把这些工作提前到构建阶段。这样生成的程序启动更快、占用内存更少，但运行时的灵活性也会降低。

对云原生应用来说，Java 因此多了一种选择。需要快速扩容、频繁启动或运行大量小实例时，Native Image 可能更合适；服务长期运行，并且更看重峰值吞吐或动态能力时，JVM 通常更合适。最终仍要根据实际业务场景选择。

本文按工程问题组织内容，讨论如何在真实项目中组合使用 Spring Boot 4.x、JDK 25 与 GraalVM Native Image。

## 1. 先判断项目是否适合 Native Image

以下场景通常能从 Native Image 中受益：

- 实例需要频繁创建、销毁或扩缩容，启动时间直接影响请求延迟；
- 单机要承载大量小实例，空闲内存比单实例峰值吞吐更重要；
- 交付物希望不依赖目标机器预装 JRE；
- CLI、批处理、短生命周期任务希望立即可用；
- 团队愿意用更长的构建时间，换取更轻的运行阶段。

遇到以下情况，则要仔细评估成本：

- 服务长期运行，JIT 充分预热后的峰值吞吐最重要；
- 依赖大量运行时扫描、动态字节码生成、插件装载或脚本能力；
- 依赖库的 Native 支持尚不成熟，又无法替换或推动上游修复；
- 发布频率很高，但 CI 内存、CPU 和构建时间预算有限；
- 线上诊断高度依赖完整 HotSpot 工具生态。

“Native 是否比 JVM 快”无法直接指导选型。更有用的是回答这些问题：

1. 最重要的是启动、内存、吞吐、尾延迟，还是构建速度？
2. 应用是否依赖运行时才知道的类型、资源和代理组合？
3. 团队能否同时维护 JVM 快速测试和 Native 真实性验证？
4. 生产环境的 OS、CPU 架构和指令集是否明确？

同一个代码库可以同时保留 JVM JAR、JVM OCI、Native executable 和 Native OCI，再根据业务约束选择交付形态。

## 2. 锁定一套可复现的 JDK 25 基线

本文使用的验证基线如下：

| 组件 | 验证基线 |
| --- | --- |
| Spring Boot | 4.1.0 |
| Spring Framework | 7.0.8，由 Spring Boot 管理 |
| Java toolchain | 25 |
| 本地 GraalVM / Native Image | GraalVM CE 25.0.2 |
| Gradle Wrapper | 9.5.1 |
| GraalVM Native Build Tools | 1.1.1 |
| 一次 Buildpacks 构建观测值 | BellSoft Liberica NIK 25.0.3 |

Spring Boot 4.1.0 官方支持 Java 17 至 26，并明确列出 GraalVM Community 25 和 Native Build Tools 1.1.1；JDK 25 要直接运行 Gradle，至少需要 Gradle 9.1。这套组合处于官方兼容范围内。升级其中任何组件时，仍要重新检查对应小版本的 [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html) 和 [Gradle Java Compatibility](https://docs.gradle.org/current/userguide/compatibility.html)。

GraalVM CE 25.0.2 是本文的复现基线，不代表当前最新安全更新。生产环境应选择组织支持的 JDK 25 更新线，并在每次 GraalVM/JDK CPU 后重建和回归。

排查业务问题前，先确认工具链：

```bash
java -version
native-image --version
./gradlew --version
./gradlew javaToolchains
./gradlew tasks --all
```

这里需要确认四件事：

- Java、Gradle Daemon 和 `native-image` 实际来自哪里；
- 本地 GraalVM 是否真的是 JDK 25；
- Gradle 是否提供 `nativeCompile`、`nativeRun` 和 `nativeTest`；
- CI 与开发机是否都通过 Wrapper 和 toolchain 使用同一条版本线。

`BP_JVM_VERSION=25` 只约束 Buildpacks 选择 JDK 25 主版本，并不锁定补丁版本、Native Image Kit 或 builder 镜像。本文记录的一次 Buildpacks 构建使用了 NIK 25.0.3，后续构建未必相同。需要可复现的生产 CI 时，应固定 builder/buildpack 镜像引用或 digest，并保存构建 BOM/SBOM；Paketo 对 `BP_JVM_VERSION` 的选择规则可见其 [Java Buildpack 文档](https://paketo.io/docs/howto/java/)。

版本锁定还应覆盖供应链本身：

- 为 Gradle Wrapper 配置 `distributionSha256Sum` 并校验 Wrapper JAR；
- 使用 dependency locking 固定解析后的传递依赖；
- 使用 dependency verification 校验依赖与插件的 checksum 或签名；
- 记录 GraalVM vendor、完整更新版本、builder/run image 与目标平台。

具体机制见 Gradle 的 [Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)、[Dependency Locking](https://docs.gradle.org/current/userguide/dependency_locking.html) 和 [Dependency Verification](https://docs.gradle.org/current/userguide/dependency_verification.html) 文档。

## 3. Spring AOT 与 Native Image 的分工

Spring Boot Native 构建中存在两层不同的 AOT：

```text
源码、配置、依赖
       │
       ▼
Spring AOT
  固化 Bean 图与条件结果
  生成 Bean 注册代码
  生成代理与 Runtime Hints
       │
       ▼
GraalVM Native Image
  闭世界可达性分析
  编译目标平台机器码
  构造 Image Heap 与精简运行时
       │
       ▼
目标 OS / 架构的原生程序
```

Spring AOT 把动态的 Spring 容器转换成更静态的初始化模型。Native Image 分析 Java 程序可达的代码、资源和动态能力，再生成目标平台程序。

这套模型有几个约束：

- 构建时的 classpath 在运行时不能再变化；
- Bean 定义和影响 Bean 是否存在的 Profile、条件属性会在构建期确定；
- 运行时 classpath 扫描不能作为发现新 Bean 的机制；
- 反射、资源、代理、JNI、序列化等动态访问必须可被推断，或通过 metadata 明确描述。

这些约束来自 Spring Framework 的 [Ahead of Time Optimizations](https://docs.spring.io/spring-framework/reference/core/aot.html) 和 GraalVM 的 [Reachability Metadata](https://www.graalvm.org/jdk25/reference-manual/native-image/metadata/) 模型。排查问题时，理解这些约束比套用构建参数更有效。

## 4. 先把 Spring 应用设计成 AOT 友好

缺少 hint 往往只是表象，问题可能来自应用在运行时才确定自身结构。遇到这种情况，应先调整应用设计，再考虑扩大 metadata。

### 4.1 让 Bean 图在构建期可确定

影响 Bean 是否存在的 Profile 和条件配置都属于构建输入。如果生产环境需要不同的 Bean 图，应分别构建并验证各个变体。一个 Native 程序不能在启动时任意切换这些结构。

对于程序化 Bean 注册，优先使用 Spring 可以在 AOT 阶段理解的 `BeanDefinitionRegistry` 或规范的 `ImportBeanDefinitionRegistrar`。避免把 Bean 创建隐藏在运行时扫描和自定义反射工厂中。

### 4.2 暴露尽可能精确的 Bean 类型

`@Bean` 方法应尽量返回精确类型，尤其当 Spring 需要根据具体类型推断注解、回调或代理行为时。这样能给 AOT 分析更多信息，也能减少人工 hint。

### 4.3 避免模糊或过度动态的创建方式

Spring 官方 AOT best practices 特别提醒：

- 多构造器类应明确首选构造器；
- 避免复杂且无法生成代码的自定义 `BeanDefinition` 属性；
- 不要依赖带任意运行时参数的 Bean 创建；
- 尽量消除循环依赖；
- 运行时扫描应改成构建期发现，或显式注册。

这些原则也能让应用结构更容易测试和维护。完整限制与建议见 [Spring Framework AOT 文档](https://docs.spring.io/spring-framework/reference/core/aot.html)。

## 5. 两条构建路径：本地 executable 与 OCI 镜像

Spring Boot 官方提供两种主要路径：使用 GraalVM Native Build Tools 生成本地 executable，或使用 Cloud Native Buildpacks 生成 OCI 镜像，详见 [Developing Your First GraalVM Native Application](https://docs.spring.io/spring-boot/how-to/native-image/developing-your-first-application.html)。

| 路径 | 主要命令 | 构建边界 | 适合场景 |
| --- | --- | --- | --- |
| 本地 Native | `./gradlew nativeCompile` | 使用本机 GraalVM 与本地 C 工具链 | 开发、诊断、直接分发 executable |
| Native OCI | `./gradlew bootBuildImage` | 在 Docker builder 中准备工具链 | CI、容器平台、统一交付 |
| JVM JAR | `./gradlew bootJar` | 使用 JVM toolchain | 快速测试、传统部署 |
| JVM OCI | 按 JVM 模式配置 `bootBuildImage` | Buildpacks JVM 路径 | 与 Native 做同口径容器对照 |

一份最小 Gradle 基线大致如下：

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'org.graalvm.buildtools.native' version '1.1.1'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

实际项目还要处理这些问题：

- 测试通过后，应逐级推广同一个 binary 或 OCI digest，避免在每个环境重新编译。
- Native executable 面向特定 OS、架构和 ABI，不能像 JAR 一样跨平台运行。
- Spring Boot 4.1 默认 Native builder 使用 Paketo tiny 运行镜像。它的攻击面较小，但通常没有 shell。需要现场调试工具时，应明确选择合适的 run image。
- Spring Boot Buildpacks 生成的镜像以非 root 用户运行，详见 [Packaging OCI Images](https://docs.spring.io/spring-boot/gradle-plugin/packaging-oci-image.html)。
- Buildpacks 缓存可以加速下载，但不能替代 builder、buildpack 和依赖版本锁定。

Native executable 未必是绝对静态、跨平台的单文件。是否动态链接以及依赖哪些系统库，需要用 `file`、`ldd` 和目标镜像实际验证。

`HTTP_PROXY`/`HTTPS_PROXY` 可以传给 builder，但代理地址属于环境配置，不应写成通用默认值，也不应把带凭据的地址写入固定配置。

## 6. 告诉 Native Image 运行时会用到什么

Native Image 只会保留构建时能够确认会被使用的代码和资源。普通方法调用通常很容易识别，但反射、动态代理和运行时拼出的资源路径没有这么直观。Reachability Metadata 的作用，就是提前告诉编译器这些内容也要保留。

以下情况经常需要 metadata：

- 程序从配置或数据库读取类名，再通过反射创建对象；
- 程序在运行时拼接 classpath 资源路径；
- 程序根据运行数据创建 JDK 动态代理；
- 程序使用 JNI、序列化或 FFM；
- 第三方库在内部使用了这些动态能力。

Metadata 只能描述构建时已经存在的类和资源，无法解决任意生成或加载新字节码的问题。本文使用的 GraalVM CE 25.0 基线不支持任意运行时类加载；GraalVM 25.1 才加入 early/experimental 的 `-H:+RuntimeClassLoading`。如果应用依赖运行时生成类，应优先把生成过程移到构建期，并针对所选 GraalVM 版本和 edition 单独验证。这个实验选项不应作为 Spring Boot 4 的默认能力。版本边界见 [GraalVM 25.1 Release Notes](https://www.graalvm.org/release-notes/25.1/)。

遇到缺失类、资源或代理的错误时，可以按这个顺序排查：

```text
这段动态访问能否改成构建时可确定的写法？
  ├─ 能：先改代码
  └─ 不能
      ↓
依赖或共享 metadata 仓库是否已经提供配置？
  ├─ 有：升级依赖或启用对应配置
  └─ 没有
      ↓
用 exact mode、nativeTest 或 tracing agent 找出缺少的注册
      ↓
只为实际使用的类型、资源或代理添加 RuntimeHints / metadata
      ↓
如果修复适用于其他项目，再反馈给依赖或 metadata 仓库
```

### 6.1 先看依赖是否已经支持 Native Image

很多依赖会自带 metadata，也可能由 GraalVM Reachability Metadata Repository 提供。可以先运行下面的任务，查看哪些依赖缺少已知的 metadata：

```bash
./gradlew listLibrariesMissingMetadata
```

Spring Boot 不会为所有第三方库补齐 hints。遇到依赖兼容问题时，优先使用已经支持 Native Image 的依赖版本，再检查共享 metadata。只有两处都没有可用配置时，才在应用中添加最小的临时修复。通用修复应反馈给依赖或 metadata 仓库，避免每个项目重复维护。

相关说明见 [Spring Boot Native Image Advanced Topics](https://docs.spring.io/spring-boot/reference/packaging/native-image/advanced-topics.html) 和 [Native Build Tools End-to-End Gradle Guide](https://graalvm.github.io/native-build-tools/latest/end-to-end-gradle-guide.html)。

### 6.2 让缺失注册在 CI 中直接失败

如果某个类型、资源或代理没有注册，问题可能要到运行时才出现。JDK 25 的 Native Image 提供 `--exact-reachability-metadata`，可以更早发现这类缺口。测试运行时还可以加入：

```text
-XX:MissingRegistrationReportingMode=Exit
```

这个选项会让程序在发现缺失注册时退出。即使第三方代码捕获了相关异常，CI 仍能看到测试失败。建议把这两个选项放进 Native 验证流程。具体语义与适用范围见 [GraalVM Reachability Metadata 文档](https://www.graalvm.org/jdk25/reference-manual/native-image/metadata/)。

### 6.3 用 tracing agent 记录实际访问

如果动态访问藏在第三方库中，或者很难从代码判断缺少什么，可以先在 JVM 上运行 tracing agent。它会记录执行过程中发生的反射、资源和代理访问：

```bash
./gradlew -Pagent bootRun
./gradlew metadataCopy \
  --task bootRun \
  --dir build/agent-metadata-review
```

使用 Spring Boot plugin 时应采集 `bootRun`，而不是 `run`；Native Build Tools 1.1.1 的 `metadataCopy` 还需要显式指定 `--dir`。

Agent 只能看到实际执行过的代码，所以测试输入必须覆盖有代表性的业务场景。生成的 JSON 也不应直接放进源码。先在 review 目录中检查并缩小注册范围，再通过原生测试验证结果。一次简单的启动测试无法覆盖所有生产路径。官方流程见 [Tracing Agent](https://www.graalvm.org/jdk25/reference-manual/native-image/guides/configure-with-tracing-agent/)。

### 6.4 只注册应用实际使用的内容

Spring 提供 Runtime Hints API，用 Java 代码描述需要保留的类型、资源和代理。`RuntimeHintsRegistrar` 应放在使用这些动态能力的功能模块附近，再通过 `@ImportRuntimeHints` 导入。

注册范围越精确越好。不要为了省事注册所有方法、整个资源目录或整个包的反射访问。下面是三个常见例子：

| 动态能力 | 为什么编译器无法直接判断 | 需要注册什么 |
| --- | --- | --- |
| 反射 | 实现类名来自配置 | 无参构造器和 `message()` 方法 |
| 资源 | classpath 路径来自配置 | `greetings/resource-greeting.txt` |
| JDK Proxy | 代理接口来自配置 | 接口的反射访问和有序的代理接口组合 |

如果资源路径直接写在代码中，Native Image 可能自行识别。路径来自运行时配置时，通常需要 hint。代理则要分别处理两件事：`Class.forName()` 需要注册类型的反射访问，`Proxy.newProxyInstance()` 需要注册确切且有顺序的接口组合。

Hints 也可以用普通 JVM 单元测试检查。Spring 提供 `RuntimeHintsPredicates`，用于断言构造器、方法、资源或代理组合是否已经注册。这类测试不能取代 `nativeTest`，但可以及时发现重命名或误删，见 [Testing Runtime Hints](https://docs.spring.io/spring-framework/reference/core/aot.html)。

## 7. 用分层测试兼顾速度和真实性

Native 测试反馈较慢，JVM 测试又覆盖不到 AOT 和 metadata 问题。Spring 官方建议把大多数单元与集成测试保留在 JVM 上，只将可能出现 Native 差异的路径放进原生验证，详见 [Testing GraalVM Native Applications](https://docs.spring.io/spring-boot/how-to/native-image/testing-native-applications.html)。

推荐采用四层测试：

| 层次 | 目标 | 建议时机 |
| --- | --- | --- |
| JVM 单元/集成测试 | 业务逻辑、Spring 常规行为与 `RuntimeHintsPredicates` | 每次提交 |
| JVM + Spring AOT | 快速验证构建期 Bean 图和 AOT 生成物 | 每次提交或 PR |
| `nativeTest` | 验证 Native 测试程序中的 AOT 与 metadata | PR、主干或定时 CI |
| 已部署 Native 冒烟/集成测试 | 验证真实端口、容器、配置和外部依赖 | 发布候选与生产前 |

JVM 上可以先验证 Spring AOT：

```bash
./gradlew bootJar
java -Dspring.aot.enabled=true \
  -jar build/libs/your-application.jar
```

再执行原生测试和构建：

```bash
./gradlew nativeTest
./gradlew nativeCompile
```

`nativeTest` 会生成并运行独立的原生测试程序，不会启动最终应用 executable 并对真实端口发请求。它可以通过 Spring 上下文和 MockMvc 路径验证反射、资源与代理 metadata，但仍需针对最终 binary 或 OCI 镜像补充部署级 smoke test。

`nativeTest` 任务来自 GraalVM Native Build Tools；Spring Boot Gradle plugin 在检测到该插件后，为原生测试配置 Spring AOT 生成物。把职责分清，排查任务缺失或 AOT 测试失败时才不会找错层次。

Native 构建昂贵时，可以把 JVM 与 JVM AOT 放在每个提交，把 `nativeTest` 放在 PR/主干，把完整多平台 Native 构建和部署测试放在发布流水线或定时任务中。

## 8. 生产构建还要考虑什么

### 8.1 区分开发构建与发布构建

Native Image 默认使用 `-O2`。开发阶段可以使用 `-Ob` 或 Native Build Tools 的 quick build 缩短反馈，但发布前必须回到正式优化级别重新测试。Community Edition 中的 `-O3` 与 `-O2` 等价。PGO 只在 Oracle GraalVM 提供，并要求使用代表性负载采集 profile。详见 [Native Image Optimizations and Performance](https://www.graalvm.org/jdk25/reference-manual/native-image/optimizations-and-performance/)。

Native 构建本身也需要纳入容量规划。根据构建输出中的 Peak RSS、GC 和线程使用情况，再调整 `-J-Xmx` 与 `--parallelism`；不要只因为“内存不足”就扩大应用级 RuntimeHints。

构建日志格式可能演进，若要建立趋势门禁，应使用 `-H:BuildOutputJSONFile=...` 保存机器可读指标。Community Edition 流水线不要无条件依赖只在 Oracle GraalVM 提供的 Build Report。

### 8.2 限制 build-time class initialization 的范围

把类提前到构建期初始化可能改善启动，但也可能把构建机上的环境变量、文件内容、随机值或其他机器状态固化进 image heap，甚至携带不合法的文件描述符和线程状态。

处理 class initialization 时：

- 先依赖 Native Image 的安全自动判断；
- 只对经过分析的具体类做定向调整；
- 使用 class initialization 诊断输出确认原因；
- 在干净环境中重新构建并验证无环境泄漏。

全局 `--initialize-at-build-time` 不适合作为通用性能模板。详见 [Class Initialization](https://www.graalvm.org/jdk25/reference-manual/native-image/optimizations-and-performance/ClassInitialization/)。

### 8.3 明确 CPU 可移植性

Native executable 面向构建时选定的目标平台和指令集。GraalVM JDK 25 在 x86-64 上的默认目标为 `x86-64-v3`：

- 同构部署、确定 CPU 型号时，才考虑 `-march=native`；
- 需要覆盖较旧 x86-64 机器时，评估 `-march=compatibility`；
- 多架构发布应在对应 runner/builder 上分别构建和测试。

CPU 兼容性应在设计交付流程时确定。相关选项见 [Native Image Optimizations and Performance](https://www.graalvm.org/jdk25/reference-manual/native-image/optimizations-and-performance/)。

### 8.4 用实际容器限制选择 GC 和堆

Native Image 默认 Serial GC，目标是小堆和较低内存占用。G1 属于 Oracle GraalVM 能力，支持平台还随版本演进：25.0 基线仅支持 Linux，25.1 起增加 macOS AArch64。Community Edition 基线不能直接复制这项配置，具体应以锁定版本的文档为准。不要照搬 HotSpot 的 GC 配置，也不要仅凭“Native RSS 更低”就省略容量测试。

在容器中应基于真实 limit 显式验证 `-Xmx`、RSS、请求并发和 OOM 行为。关于 Serial、G1 与 Epsilon 的适用边界，见 [Optimize the Memory Footprint](https://www.graalvm.org/jdk25/reference-manual/native-image/guides/optimize-memory-footprint/)；25.1 的平台变化见 [GraalVM 25.1 Release Notes](https://www.graalvm.org/release-notes/25.1/)。

### 8.5 把可观测性编进交付物

Native 并非不可观测，但部分能力必须在构建时启用。例如 JFR：

```text
--enable-monitoring=jfr
```

运行时再通过 `-XX:StartFlightRecording` 开始记录。需要 GDB、`perf` 或更清晰的原生栈时，可单独生成带 `-g` 的诊断构建；不要为了生产诊断临时换成未经同等验证的 binary。详见 [Native Image JFR](https://www.graalvm.org/jdk25/reference-manual/native-image/debugging-and-diagnostics/JFR/) 和 [Debug Information](https://www.graalvm.org/jdk25/reference-manual/native-image/debugging-and-diagnostics/DebugInfo/)。

现有 APM 如果依赖 Java Agent、JVMTI 或运行时字节码插桩，也必须单独核实 Native 支持，不能假设 JVM 配置可原样迁移。

### 8.6 安全修复意味着重新构建

Native binary 内嵌了可达的 JDK 与运行时代码。工程上的直接结论是：GraalVM/JDK CPU 或基础构建镜像更新后，应重建、重测并重新发布 Native artifact。

Cloud Native Buildpacks 的 rebase 能替换 run image 层，但不能改写已经编进 Native binary 的 JDK/GraalVM 代码。因此：

- 保存依赖、builder/buildpack 和 Native Image 版本；
- Native OCI 路径使用 Paketo/Buildpacks 生成的 SBOM；
- 若使用 Oracle GraalVM，可按其文档通过 `--enable-sbom` 内嵌或导出 CycloneDX SBOM；JDK 25 的这项 Native Image 能力不适用于 Community Edition；
- 建立基础镜像与 GraalVM 更新触发的重建流水线；
- 对重建产物执行同一套 Native 回归。

Native Image 的 SBOM edition 边界见 [GraalVM JDK 25 Release Notes](https://www.graalvm.org/release-notes/JDK_25/)，rebase 的边界见 [Cloud Native Buildpacks Rebase](https://buildpacks.io/docs/for-app-developers/concepts/rebase/)。

## 9. 性能验证：测业务目标，不测宣传语

### 9.1 一次对照测试

测试环境为 WSL2、Ryzen 7 4800H、7.5 GiB 内存和 GraalVM CE 25.0.2。测试对同一 `/hello` 接口比较 JVM 与本地 Native，以下为 5 次启动和 3 次吞吐测试的中位数：

| 指标 | JVM | Native |
| --- | ---: | ---: |
| 创建进程到首个 `/hello` 2xx | 5663.677 ms | 144.847 ms |
| 就绪时 RSS | 268588 KiB | 94624 KiB |
| 吞吐量 | 18597.64 req/s | 13427.63 req/s |
| p50 | 1.476 ms | 2.070 ms |
| p95 | 3.744 ms | 4.901 ms |
| 构建产物 | 19.12 MiB Boot JAR | 83.50 MiB executable |

在这组特定数据中：

- Native 从创建进程到 HTTP 就绪约快 39.10 倍；
- Native 就绪 RSS 低约 64.8%；
- JVM 的吞吐中位数高约 38.5%；
- Native executable 比 Boot JAR 大，但这不是完整部署体积的等价比较，因为 JVM 还需要 JRE。

### 9.2 这组数字不能证明什么

这不是普适 benchmark，至少存在以下限制：

- “启动”是新进程到首个 2xx，不是清空页缓存后的磁盘级冷启动；
- 启动轮次固定为先 JVM 后 Native，没有随机化；
- 吞吐测试先完整测 JVM，再完整测 Native；
- 客户端与服务端共享 CPU，没有 CPU pinning 或独立压测机；
- 只预热 5 秒，不能保证所有 JVM 工作负载达到长期稳态；
- 只测试无数据库和外部 I/O 的简单端点；
- 每轮 `wrk` 返回后才采样 RSS，不是连续监控到的负载峰值；
- JAR 与 executable 不是完整部署单元的同口径体积。

这组数据表明，Native 改善了这个样本的启动速度与就绪内存；JVM 在稳态吞吐和延迟上占优。结论只适用于本次测试条件。

### 9.3 生产 benchmark 应覆盖完整生命周期

建议至少记录：

| 阶段 | 指标 |
| --- | --- |
| 构建 | wall time、CPU、Peak RSS、缓存命中率 |
| 交付 | executable/JAR 大小、完整 OCI 大小、SBOM |
| 启动 | 进程创建到 ready、首次请求、扩容完成时间 |
| 稳态 | 吞吐、p50/p95/p99、CPU、RSS、GC |
| 压力 | 堆上限、OOM 行为、并发拐点、错误率 |
| 运维 | 诊断能力、修复后重建时间、多架构发布成本 |

测试时还应：

- 使用相同业务接口、数据和外部依赖；
- 固定容器 CPU/内存限制和环境变量；
- 随机化或交错 JVM/Native 轮次；
- 进行足够预热并报告完整分布；
- 尽可能使用独立负载机；
- 比较 JAR + JRE 与 Native OCI 等完整部署单元；
- 同时保留原始逐轮数据，避免只展示平均值。

## 10. 一套可复用的排障顺序

遇到问题时，先定位失败阶段，再选择工具。

### 阶段一：JVM 或普通测试失败

这通常是业务逻辑、配置或依赖问题，与 Native 无关。先保证：

```bash
./gradlew test
./gradlew bootJar
```

都通过。

### 阶段二：JVM 正常，Spring AOT 模式失败

重点检查：

- 构建期 Profile 与条件属性；
- Bean 定义、循环依赖和构造器；
- 运行时扫描或动态 Bean 注册；
- 过于宽泛的 `@Bean` 返回类型。

先在 JVM AOT 模式复现，比等待完整 Native 编译更快。

### 阶段三：Spring AOT 正常，Native 编译失败

重点检查：

- JDK、Native Image、Gradle 与插件版本；
- 本地 C 工具链和目标平台；
- 构建内存、并行度和磁盘；
- 不受支持的字节码、JNI 或运行时类生成；
- class initialization 冲突。

必要时启用 `--diagnostics-mode`，并保留完整 Native Image build output。

### 阶段四：Native 编译成功，程序运行失败

按以下顺序处理：

1. 运行 `listLibrariesMissingMetadata`；
2. 启用 exact reachability metadata 检测；
3. 根据异常区分反射、资源、代理、JNI、序列化或平台能力；
4. 用 tracing agent 覆盖代表性路径；
5. 添加最小 RuntimeHints 或条件 metadata；
6. 用 `nativeTest` 和最终交付物双重回归。

网络协议、charset、locale 和安全 provider 也可能需要显式支持，但只应启用应用使用的部分，避免把“包含全部”当作默认修复。GraalVM 的官方排障入口见 [Troubleshoot Run-Time Errors](https://www.graalvm.org/jdk25/reference-manual/native-image/guides/troubleshoot-run-time-errors/)。

## 11. 上线前检查清单

- [ ] Spring Boot、JDK、Gradle、Native Build Tools 与 builder 版本已锁定并记录
- [ ] Gradle Wrapper checksum、依赖锁与依赖校验已启用
- [ ] 构建期 Profile 和条件配置与目标环境一致
- [ ] 没有依赖运行时 classpath 扫描来改变 Bean 图
- [ ] 第三方依赖的 Native 支持和共享 metadata 已核查
- [ ] RuntimeHints 精确、就近，且有测试覆盖
- [ ] `RuntimeHintsPredicates` 与部署级动态路径都已覆盖
- [ ] JVM、JVM AOT、`nativeTest` 与部署级 smoke test 均通过
- [ ] exact metadata 检测已进入 CI
- [ ] 每个目标 OS/架构/CPU 基线都有对应构建与验证
- [ ] 堆、GC、RSS 和容器 OOM 行为已在真实 limit 下测量
- [ ] JFR、debug symbols、heap dump 等诊断能力按需规划
- [ ] SBOM、builder/buildpack BOM 和构建日志可追溯
- [ ] GraalVM/JDK 安全更新能够触发重建与回归
- [ ] JVM 与 Native 的选择基于真实业务 benchmark，而不是单一启动数字

## 结论

Spring Boot 4、JDK 25 与 GraalVM Native Image 已经具备完整的构建、AOT、测试和容器交付链路，同时也有明确的使用约束。Native Image 提升了 Java 在云原生环境中的竞争力，尤其是在启动速度、实例密度和短生命周期更受重视的场景中。代价是更多工作被移到构建期，团队也要承担相应的工程复杂度。

一套可维护的 Native 工程需要在构建期确定应用结构，用最小 metadata 描述动态能力，并分层运行 JVM 与 Native 测试。性能、平台兼容性、可观测性和安全重建也都属于交付要求。

重视启动速度、实例密度或短生命周期的业务可以优先评估 Native Image。更看重长期峰值吞吐、运行时动态性和 HotSpot 运维生态时，JVM 可能更合适。最终选择应以实际工作负载和生产约束为准。
