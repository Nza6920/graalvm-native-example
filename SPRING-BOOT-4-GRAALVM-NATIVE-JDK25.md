---
title: "Spring Boot 4.x + GraalVM Native Image + JDK 25：从能构建到可生产"
date: 2026-07-24
description: "围绕 Spring Boot 4、JDK 25 与 GraalVM Native Image，系统梳理选型、AOT 设计、Reachability Metadata、测试、容器交付、可观测性与性能验证。"
tags:
  - Spring Boot 4
  - JDK 25
  - GraalVM
  - Native Image
  - AOT
---

# Spring Boot 4.x + GraalVM Native Image + JDK 25：从能构建到可生产

GraalVM Native Image 最吸引人的标签通常是“毫秒级启动”和“更低内存”，但真正决定项目能否稳定落地的，并不是第一次执行 `nativeCompile` 是否成功，而是应用能否接受一份更严格的构建期契约：

> classpath、Bean 图以及大部分运行时会触达的代码和资源，都应在构建阶段变得可知、可验证、可复现。

因此，Native Image 不是“更快的 JVM”，而是一种不同的部署模型。它把许多原本由 JVM 在运行时完成的工作前移到了构建期，也把一部分运行时灵活性转换成了启动速度、内存密度和交付确定性。

本文以仓库中的 `native-demo` 作为可复现案例，但不按课程或实验顺序展开。主线只有一条：如何把 **Spring Boot 4.x、JDK 25 与 GraalVM Native Image** 组合成一套可用于真实项目的工程方法。

## 1. 先做选型：Native Image 解决的是什么问题

Native Image 的价值主要出现在以下场景：

- 实例需要频繁创建、销毁或扩缩容，启动时间直接影响请求延迟；
- 单机要承载大量小实例，空闲内存比单实例峰值吞吐更重要；
- 交付物希望不依赖目标机器预装 JRE；
- CLI、批处理、短生命周期任务希望立即可用；
- 团队愿意用更长的构建时间，换取更轻的运行阶段。

以下场景则应谨慎评估：

- 服务长期运行，JIT 充分预热后的峰值吞吐最重要；
- 依赖大量运行时扫描、动态字节码生成、插件装载或脚本能力；
- 依赖库的 Native 支持尚不成熟，又无法替换或推动上游修复；
- 发布频率很高，但 CI 内存、CPU 和构建时间预算有限；
- 线上诊断高度依赖完整 HotSpot 工具生态。

选型时不要问“Native 是否比 JVM 快”，而要问：

1. 最重要的是启动、内存、吞吐、尾延迟，还是构建速度？
2. 应用是否依赖运行时才知道的类型、资源和代理组合？
3. 团队能否同时维护 JVM 快速测试和 Native 真实性验证？
4. 生产环境的 OS、CPU 架构和指令集是否明确？

Native 与 JVM 不是非此即彼。同一个代码库可以保留 JVM JAR、JVM OCI、Native executable 和 Native OCI 多种交付形态，再由业务约束决定使用哪一种。

## 2. 锁定一套可复现的 JDK 25 基线

本仓库使用的基线如下：

| 组件 | 仓库基线 |
| --- | --- |
| Spring Boot | 4.1.0 |
| Spring Framework | 7.0.8，由 Spring Boot 管理 |
| Java toolchain | 25 |
| 本地 GraalVM / Native Image | GraalVM CE 25.0.2 |
| Gradle Wrapper | 9.5.1 |
| GraalVM Native Build Tools | 1.1.1 |
| 一次 Buildpacks 构建观测值 | BellSoft Liberica NIK 25.0.3 |

Spring Boot 4.1.0 官方支持 Java 17 至 26，并明确列出 GraalVM Community 25 和 Native Build Tools 1.1.1；JDK 25 要直接运行 Gradle，至少需要 Gradle 9.1。由此可见，上述组合处于官方兼容范围内，但升级任意一边时仍应重新检查对应小版本的 [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html) 和 [Gradle Java Compatibility](https://docs.gradle.org/current/userguide/compatibility.html)。

其中 GraalVM CE 25.0.2 是仓库的历史复现基线，不代表当前最新安全更新。生产环境应选择组织支持的 JDK 25 更新线，并在每次 GraalVM/JDK CPU 后重建和回归。

项目的版本配置可见：

- [Gradle 构建配置](native-demo/build.gradle)
- [Gradle Wrapper 配置](native-demo/gradle/wrapper/gradle-wrapper.properties)
- [本地与 Buildpacks 工具链记录](learning-records/0003-local-native-compile-and-native-test.md)

开始排查业务问题前，先建立工具链事实：

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

`BP_JVM_VERSION=25` 只约束 Buildpacks 选择 JDK 25 主版本，并不锁定补丁版本、Native Image Kit 或 builder 镜像。本仓库观测到的 NIK 25.0.3 是一次构建事实，不是未来构建保证。生产 CI 若需要更强的可复现性，应固定 builder/buildpack 镜像引用或 digest，并保存构建 BOM/SBOM；Paketo 对 `BP_JVM_VERSION` 的选择规则可见其 [Java Buildpack 文档](https://paketo.io/docs/howto/java/)。

版本锁定还应覆盖供应链本身：

- 为 Gradle Wrapper 配置 `distributionSha256Sum` 并校验 Wrapper JAR；
- 使用 dependency locking 固定解析后的传递依赖；
- 使用 dependency verification 校验依赖与插件的 checksum 或签名；
- 记录 GraalVM vendor、完整更新版本、builder/run image 与目标平台。

具体机制见 Gradle 的 [Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)、[Dependency Locking](https://docs.gradle.org/current/userguide/dependency_locking.html) 和 [Dependency Verification](https://docs.gradle.org/current/userguide/dependency_verification.html) 文档。

## 3. 心智模型：Spring AOT 与 Native Image 各做一半

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

Spring AOT 负责把动态的 Spring 容器转换成更静态的初始化模型；Native Image 负责分析 Java 程序真正可达的代码、资源和动态能力，并生成目标平台程序。

这带来几个重要约束：

- 构建时的 classpath 在运行时不能再变化；
- Bean 定义和影响 Bean 是否存在的 Profile、条件属性会在构建期确定；
- 运行时 classpath 扫描不能作为发现新 Bean 的机制；
- 反射、资源、代理、JNI、序列化等动态访问必须可被推断，或通过 metadata 明确描述。

这些约束来自 Spring Framework 的 [Ahead of Time Optimizations](https://docs.spring.io/spring-framework/reference/core/aot.html) 和 GraalVM 的 [Reachability Metadata](https://www.graalvm.org/jdk25/reference-manual/native-image/metadata/) 模型。理解它们，比背诵某个构建参数更重要。

## 4. 先把 Spring 应用设计成 AOT 友好

很多 Native 问题表面上是“缺少 hint”，根因却是应用在运行时才决定自己的结构。优先改进应用设计，通常比扩大 metadata 更可靠。

### 4.1 让 Bean 图在构建期可确定

影响 Bean 是否存在的 Profile 和条件配置，应视为构建输入。如果生产环境要使用不同 Bean 图，应为这些变体分别构建和验证，而不是假设一个 Native 程序能在启动时任意切换。

对于程序化 Bean 注册，优先使用 Spring 可以在 AOT 阶段理解的 `BeanDefinitionRegistry` 或规范的 `ImportBeanDefinitionRegistrar`。避免把 Bean 创建隐藏在运行时扫描和自定义反射工厂中。

### 4.2 暴露尽可能精确的 Bean 类型

`@Bean` 方法不要为了抽象而返回过于宽泛的接口，尤其当 Spring 需要根据具体类型推断注解、回调或代理行为时。精确的返回类型能给 AOT 分析更多信息，也减少人工 hint。

### 4.3 避免模糊或过度动态的创建方式

Spring 官方 AOT best practices 特别提醒：

- 多构造器类应明确首选构造器；
- 避免复杂且无法生成代码的自定义 `BeanDefinition` 属性；
- 不要依赖带任意运行时参数的 Bean 创建；
- 尽量消除循环依赖；
- 运行时扫描应改成构建期发现，或显式注册。

这些原则不只服务于 Native Image，也会让应用结构更容易测试和维护。完整限制与建议见 [Spring Framework AOT 文档](https://docs.spring.io/spring-framework/reference/core/aot.html)。

## 5. 两条构建路径：本地 executable 与 OCI 镜像

Spring Boot 官方提供两种主要路径：使用 GraalVM Native Build Tools 生成本地 executable，或使用 Cloud Native Buildpacks 生成 OCI 镜像，详见 [Developing Your First GraalVM Native Application](https://docs.spring.io/spring-boot/how-to/native-image/developing-your-first-application.html)。

| 路径 | 主要命令 | 构建边界 | 适合场景 |
| --- | --- | --- | --- |
| 本地 Native | `./gradlew nativeCompile` | 使用本机 GraalVM 与本地 C 工具链 | 开发、诊断、直接分发 executable |
| Native OCI | `./gradlew bootBuildImage` | 在 Docker builder 中准备工具链 | CI、容器平台、统一交付 |
| JVM JAR | `./gradlew bootJar` | 使用 JVM toolchain | 快速测试、传统部署 |
| JVM OCI | 项目中使用 `-PjvmImage` 构建 | Buildpacks JVM 路径 | 与 Native 做同口径容器对照 |

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

实际项目还需要考虑以下问题：

- **构建一次、逐级晋级**：测试通过后推广同一个 binary 或 OCI digest，不要在每个环境重新编译。
- **目标平台构建**：Native executable 面向特定 OS、架构和 ABI，不是跨平台 JAR。
- **容器最小化**：Spring Boot 4.1 默认 Native builder 使用 Paketo tiny 运行镜像，攻击面较小，但通常没有 shell；需要现场调试工具时应显式选择合适的 run image，而不是临时假设容器内什么都有。
- **非 root 运行**：Spring Boot Buildpacks 生成的镜像以非 root 用户运行，详见 [Packaging OCI Images](https://docs.spring.io/spring-boot/gradle-plugin/packaging-oci-image.html)。
- **缓存不是版本锁**：Buildpacks 缓存可以加速下载，但不能替代 builder、buildpack 和依赖版本锁定。

还要避免两个常见误解。第一，Native executable 不等于“绝对静态、跨平台的单文件”；是否动态链接以及依赖哪些系统库，要用 `file`、`ldd` 和目标镜像实际验证，[本仓库当前产物](learning-records/0003-local-native-compile-and-native-test.md)就是 Linux x86-64 动态链接 ELF。第二，`HTTP_PROXY`/`HTTPS_PROXY` 可以传给 builder，但代理地址属于环境配置；本仓库中的 `host.docker.internal:7897` 是特定开发机设置，不应复制为通用默认值或写入带凭据的固定配置。

## 6. Reachability Metadata：从“补 hint”升级为治理流程

Native Image 可以静态推断很多直接调用，但以下行为通常需要额外信息：

- 配置或数据库中读取类名后再反射；
- 运行时拼接 classpath 资源路径；
- 根据运行数据组合 JDK 动态代理接口；
- JNI、序列化或 FFM；
- 第三方库内部隐藏的动态访问。

运行时生成或加载新字节码则是另一类问题，不能简单靠 metadata 解决。本仓库的 GraalVM CE 25.0 基线不支持任意运行时类加载；GraalVM 25.1 才加入 early/experimental 的 `-H:+RuntimeClassLoading`。依赖这类能力时，应优先改为构建期生成，并针对所选 GraalVM 版本和 edition 做专项验证，不能把实验选项当作 Spring Boot 4 的默认能力。版本边界见 [GraalVM 25.1 Release Notes](https://www.graalvm.org/release-notes/25.1/)。

正确的处理顺序不是“看到异常就注册整个包”，而是：

```text
能否改为静态、常量或框架可推断的访问？
  ├─ 能：优先改代码
  └─ 不能
      ↓
依赖是否已提供或共享仓库是否已有 metadata？
  ├─ 有：升级/启用对应版本
  └─ 没有
      ↓
用 exact mode、原生测试或 tracing agent 找到真实缺口
      ↓
添加最小、就近、带条件的 RuntimeHints / metadata
      ↓
把可复用修复贡献给依赖或共享仓库
```

### 6.1 先检查依赖，而不是先手写

Native Build Tools 提供依赖 metadata 检查：

```bash
./gradlew listLibrariesMissingMetadata
```

Spring Boot 不会为所有第三方库代写 hints，而是依赖库自身和 GraalVM Reachability Metadata Repository。遇到依赖兼容问题时，优先顺序应是：

1. 使用已经提供 Native 支持的依赖版本；
2. 检查共享 metadata；
3. 在应用层添加最小临时修复；
4. 将通用修复反馈给依赖或 metadata 仓库。

相关说明见 [Spring Boot Native Image Advanced Topics](https://docs.spring.io/spring-boot/reference/packaging/native-image/advanced-topics.html) 和 [Native Build Tools End-to-End Gradle Guide](https://graalvm.github.io/native-build-tools/latest/end-to-end-gradle-guide.html)。

### 6.2 JDK 25 尽早使用 exact metadata 检测

JDK 25 的 Native Image 支持 `--exact-reachability-metadata`，用于更早、更精确地暴露缺失注册。测试运行时还可以使用：

```text
-XX:MissingRegistrationReportingMode=Exit
```

这样即使第三方代码捕获并吞掉了缺失注册异常，测试仍可失败。它们应进入 CI 的 Native 验证路径，而不是只在故障后手工执行。具体语义与适用范围见 [GraalVM Reachability Metadata 文档](https://www.graalvm.org/jdk25/reference-manual/native-image/metadata/)。

### 6.3 tracing agent 是发现工具，不是最终答案

当动态路径来自第三方库或很难静态定位时，可以让 tracing agent 在 JVM 上记录实际访问：

```bash
./gradlew -Pagent bootRun
./gradlew metadataCopy \
  --task bootRun \
  --dir build/agent-metadata-review
```

本项目应用了 Spring Boot plugin，因此可采集的应用启动任务是 `bootRun`，不是 `run`；Native Build Tools 1.1.1 的 `metadataCopy` 还需要显式指定 `--dir`。Agent 只能记录被执行过的路径，因此输入必须覆盖有代表性的业务场景；生成结果应先在 review 目录中人工审查、收窄并回归测试，再决定是否放入源码。不要把一次冒烟运行得到的宽泛 JSON 直接视为完整生产 metadata。官方流程见 [Tracing Agent](https://www.graalvm.org/jdk25/reference-manual/native-image/guides/configure-with-tracing-agent/)。

### 6.4 Spring 项目优先使用就近、最小的 RuntimeHints

`RuntimeHintsRegistrar` 应靠近真正需要它的功能模块，并通过 `@ImportRuntimeHints` 就近导入。注册范围只覆盖实际需要的成员、资源和代理组合，避免 `allDeclaredMethods`、整目录资源或整包反射之类的兜底做法。

本仓库包含三个典型案例：

| 动态能力 | 运行时输入 | 最小修复 | 案例代码 |
| --- | --- | --- | --- |
| 反射 | 配置中的实现类名 | 精确注册无参构造器和 `message()` | [GreetingRuntimeHints](native-demo/src/main/java/com/example/nativedemo/greeting/reflective/GreetingRuntimeHints.java) |
| 资源 | 配置中的 classpath 路径 | 精确注册 `greetings/resource-greeting.txt` | [ResourceGreetingRuntimeHints](native-demo/src/main/java/com/example/nativedemo/greeting/resource/ResourceGreetingRuntimeHints.java) |
| JDK Proxy | 配置中的接口名 | 分别注册接口反射与有序代理接口组合 | [ProxyGreetingRuntimeHints](native-demo/src/main/java/com/example/nativedemo/greeting/proxy/ProxyGreetingRuntimeHints.java) |

资源使用常量路径时，Native Image 可能直接推断；这里需要 hint 的关键，是路径来自运行时配置。代理也要区分两件事：`Class.forName()` 需要类型可反射访问，`Proxy.newProxyInstance()` 还需要注册确切且有顺序的接口组合。

Hints 本身也应有快速单元测试。Spring 提供 `RuntimeHintsPredicates`，可以在普通 JVM 测试中断言某个构造器、方法、资源或代理组合是否已注册。它不能取代 `nativeTest`，但能在每次提交时及时发现重命名或误删，见 [Testing Runtime Hints](https://docs.spring.io/spring-framework/reference/core/aot.html)。

## 7. 测试策略：快反馈与真实验证要同时存在

全部测试都改成 Native 会让反馈过慢；只在 JVM 上测试又会漏掉 AOT 和 metadata 问题。Spring 官方建议把大多数单元与集成测试继续放在 JVM 上，只把可能出现 Native 差异的路径放进原生验证，详见 [Testing GraalVM Native Applications](https://docs.spring.io/spring-boot/how-to/native-image/testing-native-applications.html)。

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
  -jar build/libs/native-demo-0.0.1-SNAPSHOT.jar
```

再执行原生测试和构建：

```bash
./gradlew nativeTest
./gradlew nativeCompile
```

需要特别区分：`nativeTest` 会生成并运行独立的原生测试程序，它不是启动最终应用 executable 后对真实端口发请求。本仓库当前 5 个测试通过了 Spring 上下文和 MockMvc 路径，能够验证反射、资源与代理 metadata，但仍应补充针对最终 binary 或 OCI 镜像的部署级 smoke test。

`nativeTest` 任务来自 GraalVM Native Build Tools；Spring Boot Gradle plugin 在检测到该插件后，为原生测试配置 Spring AOT 生成物。把职责分清，排查任务缺失或 AOT 测试失败时才不会找错层次。

Native 构建昂贵时，可以把 JVM 与 JVM AOT 放在每个提交，把 `nativeTest` 放在 PR/主干，把完整多平台 Native 构建和部署测试放在发布流水线或定时任务中。

## 8. 从“能运行”到“适合生产”

### 8.1 区分开发构建与发布构建

Native Image 默认使用 `-O2`。开发阶段可以使用 `-Ob` 或 Native Build Tools 的 quick build 缩短反馈，但发布前必须回到正式优化级别重新测试。`-O3` 和 PGO 也不是 Community Edition 的通用免费加速按钮：CE 中 `-O3` 与 `-O2` 等价，PGO 只在 Oracle GraalVM 提供，并要求使用代表性负载采集 profile。详见 [Native Image Optimizations and Performance](https://www.graalvm.org/jdk25/reference-manual/native-image/optimizations-and-performance/)。

Native 构建本身也需要纳入容量规划。根据构建输出中的 Peak RSS、GC 和线程使用情况，再调整 `-J-Xmx` 与 `--parallelism`；不要只因为“内存不足”就扩大应用级 RuntimeHints。

构建日志格式可能演进，若要建立趋势门禁，应使用 `-H:BuildOutputJSONFile=...` 保存机器可读指标。Community Edition 流水线不要无条件依赖只在 Oracle GraalVM 提供的 Build Report。

### 8.2 不要全局启用 build-time class initialization

把类提前到构建期初始化可能改善启动，但也可能把构建机上的环境变量、文件内容、随机值或其他机器状态固化进 image heap，甚至携带不合法的文件描述符和线程状态。

最佳实践是：

- 先依赖 Native Image 的安全自动判断；
- 只对经过分析的具体类做定向调整；
- 使用 class initialization 诊断输出确认原因；
- 在干净环境中重新构建并验证无环境泄漏。

不要把全局 `--initialize-at-build-time` 当作性能模板。详见 [Class Initialization](https://www.graalvm.org/jdk25/reference-manual/native-image/optimizations-and-performance/ClassInitialization/)。

### 8.3 明确 CPU 可移植性

Native executable 面向构建时选定的目标平台和指令集。GraalVM JDK 25 在 x86-64 上的默认目标为 `x86-64-v3`：

- 同构部署、确定 CPU 型号时，才考虑 `-march=native`；
- 需要覆盖较旧 x86-64 机器时，评估 `-march=compatibility`；
- 多架构发布应在对应 runner/builder 上分别构建和测试。

这是交付契约的一部分，而不是上线前才检查的细节。相关选项见 [Native Image Optimizations and Performance](https://www.graalvm.org/jdk25/reference-manual/native-image/optimizations-and-performance/)。

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

### 9.1 本仓库的可复现实测

仓库在 WSL2、Ryzen 7 4800H、7.5 GiB 内存、GraalVM CE 25.0.2 环境中，对同一 `/hello` 接口做了 JVM 与本地 Native 对照。以下为 5 次启动和 3 次吞吐测试的中位数：

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

原始数据与环境说明：

- [启动数据](native-demo/benchmark/results/2026-07-24/startup.tsv)
- [吞吐数据](native-demo/benchmark/results/2026-07-24/throughput.tsv)
- [产物数据](native-demo/benchmark/results/2026-07-24/artifacts.tsv)
- [测试环境](native-demo/benchmark/results/2026-07-24/environment.md)

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

所以正确结论不是“Native 更快”或“JVM 吞吐更高”，而是：**Native 显著改善了这个样本的启动与就绪内存，JVM 则在这个样本的稳态吞吐和延迟上占优。**

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

网络协议、charset、locale 和安全 provider 也可能需要显式支持，但只应启用应用真正使用的部分，避免把“包含全部”当作默认修复。GraalVM 的官方排障入口见 [Troubleshoot Run-Time Errors](https://www.graalvm.org/jdk25/reference-manual/native-image/guides/troubleshoot-run-time-errors/)。

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

## 结语

Spring Boot 4、JDK 25 与 GraalVM Native Image 已经能提供成熟的构建、AOT、测试和容器交付链路，但“成熟”不等于“没有约束”。

真正可靠的 Native 项目通常具备四个特征：

1. 应用结构在构建期足够明确；
2. 动态能力通过最小 metadata 被治理，而不是被隐藏；
3. JVM 快反馈与 Native 真实性验证分层运行；
4. 性能、平台、可观测性和安全重建都被当作交付契约。

如果业务最在意启动速度、实例密度或短生命周期，Native Image 很可能值得投入；如果最在意长期峰值吞吐、运行时动态性和成熟的 HotSpot 运维生态，JVM 仍可能是更好的答案。最专业的选择不是站队，而是用自己的工作负载和生产约束做决定。
