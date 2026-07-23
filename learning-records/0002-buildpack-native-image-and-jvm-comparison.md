# 第 1 课：构建 Spring Boot Native Image 并与 JVM 对比

日期：2026-07-23

## 本课目标

使用 Spring Boot 4.1、Gradle 9.5.1 和 JDK 25：

- 构建一个可以运行的 Spring Boot Native OCI 镜像；
- 构建同一服务的普通 JVM OCI 镜像；
- 验证两个镜像的 `/hello` 接口行为一致；
- 对比运行内存、线程数和镜像大小；
- 理解 Buildpacks、JRE、AOT、代理与 Spring AOP 在 Native 模式下的影响。

## 最终技术基线

| 项目 | 版本或配置 |
|---|---|
| Spring Boot | 4.1.0 |
| Spring Framework | 7.0.8 |
| Gradle Wrapper | 9.5.1 |
| Java Toolchain | 25 |
| GraalVM Native Build Tools | 1.1.1 |
| Native Image JDK | BellSoft Liberica NIK 25.0.3（Buildpacks） |
| Buildpacks Builder | `paketobuildpacks/builder-noble-java-tiny` |

项目目录：

```text
native-demo/
```

测试接口：

```text
GET /hello
```

## 两条 Native 构建路径

### 本地原生可执行文件

```bash
./gradlew nativeCompile
```

典型输出位置：

```text
build/native/nativeCompile/native-demo
```

这个任务使用本机安装的 GraalVM 和 `native-image`，生成可以直接运行的本地可执行文件。

### Buildpacks Native OCI 镜像

```bash
./gradlew bootBuildImage
```

这个任务在 Docker builder 容器中完成构建，生成 OCI/Docker 镜像：

```text
native-demo:0.0.1-SNAPSHOT
```

两者的区别：

| 任务 | 使用的工具环境 | 输出 |
|---|---|---|
| `nativeCompile` | 本机 GraalVM | 本地原生可执行文件 |
| `bootBuildImage` | Docker Buildpacks builder | 包含原生程序的 OCI 镜像 |

本机通过 SDKMAN 选择的 JDK 不会自动成为 Buildpacks 内部的 JDK。两套环境必须分别确认。

## JDK 25 配置与排查

Gradle 编译工具链：

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

Buildpacks 内部 JDK：

```groovy
tasks.named('bootBuildImage') {
    environment['BP_JVM_VERSION'] = '25'
}
```

看到下面的日志时：

```text
$BP_JVM_VERSION 21
```

不能直接判断整个项目正在使用 JDK 21。它可能只是 Buildpack 的默认配置。需要继续看实际选择结果：

```text
Using Java version 25 from BP_JVM_VERSION
```

或者：

```text
Using Java version 25 extracted from MANIFEST.MF
```

本课最终显式设置了 `BP_JVM_VERSION=25`，避免依赖默认值或清单推导。

`BP_JVM_TYPE=JRE` 是普通 JVM 镜像的默认运行时类型。Native 构建最终运行的是原生可执行文件，不会把完整 JRE 放入最终 Native 镜像。

## 为什么第一次构建很慢

第一次 `bootBuildImage` 需要下载和准备：

- Builder 和 Run Image；
- Paketo Buildpacks；
- BellSoft Liberica NIK 25.0.3；
- Syft 等辅助工具；
- Spring Boot 应用层；
- Native Image 编译所需环境。

本次 NIK 压缩包大小：

```text
346,115,644 bytes
≈ 346.1 MB
≈ 330 MiB
```

第一次失败前，下载速度一度只有：

```text
250～300 KB/s
```

后来没有代理时，速度甚至降到：

```text
约 90 KB/s
```

错误表现：

```text
unable to invoke layer creator
unable to get dependency BellSoft Liberica NIK
```

这不是 Java 编译错误，而是 Buildpack 下载 NIK 依赖失败。

下载成功并完成构建后，NIK 层会进入 Buildpacks/Docker 缓存。以后只修改业务代码时通常不需要重新下载 NIK，但 Native Image 编译本身仍然需要时间。

## 两类网络错误要分开

### Docker daemon 拉取 Builder 失败

错误：

```text
Get "https://registry-1.docker.io/v2/": EOF
```

这发生在 builder 启动之前，是 Docker daemon 访问 Docker Hub 失败，不是 Buildpack 内部下载失败。

本地已经存在 builder 时可以使用：

```bash
--pullPolicy=IF_NOT_PRESENT
```

避免每次构建都向 Docker Hub 检查 `latest`。

### Builder 内部下载 GitHub 依赖失败

BellSoft NIK 是 builder 容器内部从 GitHub 下载的。Docker daemon 的代理不会自动传给 builder 容器，因此必须通过 `bootBuildImage.environment` 显式设置。

## 容器如何访问宿主机代理

宿主机 shell 使用：

```text
http://127.0.0.1:7897
```

但容器内的 `127.0.0.1` 指向容器自己。容器访问宿主机应使用 Docker 提供的特殊域名：

```text
http://host.docker.internal:7897
```

本课已从 builder 容器中实际验证：

- `host.docker.internal` 能正确解析；
- builder 通过 `host.docker.internal:7897` 访问 GitHub 返回 HTTP 200。

项目中的代理配置：

```groovy
tasks.named('bootBuildImage') {
    def buildProxy = 'http://host.docker.internal:7897'
    def buildNoProxy = '127.0.0.1,localhost,::1'

    environment['HTTP_PROXY'] = buildProxy
    environment['HTTPS_PROXY'] = buildProxy
    environment['http_proxy'] = buildProxy
    environment['https_proxy'] = buildProxy
    environment['NO_PROXY'] = buildNoProxy
    environment['no_proxy'] = buildNoProxy
}
```

切换到更快的代理节点后，NIK 下载速度提高到约：

```text
3 MB/s
```

相对原来的 `16～19 KB/s` 提升超过 150 倍，并成功完成校验和解压：

```text
Verifying checksum
Expanding to /layers/.../native-image-svm
```

## 保留完整构建日志

终端输出和日志文件同时保留：

```bash
set -o pipefail
./gradlew bootBuildImage \
  --pullPolicy=IF_NOT_PRESENT \
  --environment BP_LOG_LEVEL=DEBUG \
  --console=plain \
  --stacktrace 2>&1 | tee bootBuildImage-debug.log
```

实时观察：

```bash
tail -f bootBuildImage-debug.log
```

监控 builder：

```bash
docker ps
docker stats --no-stream <container>
docker logs --tail 100 <container>
```

判断状态：

- 下载阶段：CPU 低，`NET I/O` 持续增加；
- Native Image 编译阶段：CPU 和内存明显升高；
- 疑似卡住：日志不更新且网络、CPU、磁盘长期无变化。

## 构建普通 JVM 镜像

应用 GraalVM Native Build Tools 插件后，Spring Boot 会把 `bootBuildImage` 自动配置为 Native 构建。

为了得到真正的普通 JVM 对照，项目在 `-PjvmImage` 模式下不应用 GraalVM 插件：

```groovy
id 'org.graalvm.buildtools.native' version '1.1.1' apply false

if (!providers.gradleProperty('jvmImage').isPresent()) {
    apply plugin: 'org.graalvm.buildtools.native'
}
```

构建 JVM 镜像：

```bash
./gradlew -PjvmImage bootBuildImage \
  --imageName=native-demo:jvm \
  --pullPolicy=IF_NOT_PRESENT
```

任务链验证结果：

- JVM 模式：`compileJava → bootJar → bootBuildImage`；
- Native 模式：额外包含 `processAot`、`compileAotJava`、`collectReachabilityMetadata`。

## 运行两个版本

Native：

```bash
docker run --rm \
  --name native-demo \
  -p 8080:8080 \
  native-demo:0.0.1-SNAPSHOT
```

JVM：

```bash
docker run --rm \
  --name native-demo-jvm \
  -p 8081:8080 \
  native-demo:jvm
```

验证行为一致：

```bash
curl http://localhost:8080/hello
curl http://localhost:8081/hello
```

## 运行内存对比

命令：

```bash
docker stats --no-stream native-demo native-demo-jvm
```

实测：

| 指标 | Native | JVM | 对比 |
|---|---:|---:|---:|
| 空闲内存 | 38.92 MiB | 193.3 MiB | JVM 约为 Native 的 5 倍 |
| PIDS | 18 | 47 | JVM 约为 Native 的 2.6 倍 |
| CPU 瞬时值 | 0.03% | 0.13% | 都接近空闲，不能据此比较性能 |

Native 比 JVM 少使用：

```text
193.3 - 38.92 = 154.38 MiB
```

空闲内存约节省 80%。

CPU 的单次空闲采样不能用于判断吞吐量。公平的性能比较还需要：

- 相同请求和并发；
- 相同容器资源限制；
- 区分冷启动、JVM 预热和稳定运行；
- 对比延迟、吞吐量、峰值内存和 GC。

## 镜像大小对比

命令：

```bash
docker image ls native-demo
```

实测：

| 镜像 | 大小 |
|---|---:|
| Native | 128 MB |
| JVM | 338 MB |

Native 小：

```text
338 - 128 = 210 MB
```

约节省 62%，JVM 镜像约为 Native 的 2.64 倍。

通过 `docker history` 查看实际层：

### Native 镜像

| 主要层 | 大小 |
|---|---:|
| 最小 Linux 基础层 | 24.9 MB |
| Native 应用层 | 95 MB |
| Buildpacks launcher | 2.93 MB |
| CA helper | 4.65 MB |

### JVM 镜像

| 主要层 | 大小 |
|---|---:|
| 最小 Linux 基础层 | 24.9 MB |
| BellSoft Liberica JRE | 276 MB |
| 应用 JAR slices | 约 20 MB |
| Spring/Java/CA helper 与 launcher | 约 17 MB |

Native 的应用主体比 JAR 大，因为它包含机器码、必要的 Java 类和精简运行时；但它不需要额外携带 276 MB 的完整 JRE，所以最终镜像更小。

`docker image ls` 中显示 `CREATED 46 years ago` 不表示镜像真的构建于 46 年前。Buildpacks 使用固定历史时间戳生成可复现镜像。

## 为什么 Native 不需要 JRE

普通 JVM：

```text
.java
  ↓ javac
.class 字节码
  ↓ JVM 解释或 JIT 编译
机器码
  ↓
CPU
```

Native Image：

```text
.java
  ↓ javac
.class 字节码
  ↓ native-image 在构建期进行静态分析和 AOT 编译
Linux 原生机器码可执行文件
  ↓
CPU
```

Native 可执行文件不需要完整 JRE，但并非没有 Java 运行时能力。它内部包含应用真正需要的精简 SubstrateVM 组件：

- Java 对象堆；
- 垃圾回收器；
- 线程和异常处理；
- 必要的反射、代理与资源元数据；
- 被静态分析判断为可达的 JDK、Spring 和业务代码。

Native 的本质是把通用 JRE 换成“只为这个应用准备的程序和精简运行时”。

## 为什么 Native 运行内存更低

Native Image 把大量成本从运行期移动到构建期：

- 不需要完整 HotSpot JVM；
- 不需要字节码解释器；
- 不需要 JIT 编译线程和性能分析数据；
- 不需要运行时 Code Cache；
- 类加载和 Metaspace 元数据更少；
- 闭世界静态分析会删除不可达代码；
- 一部分对象可以提前放入 image heap；
- 运行时线程数量更少。

代价：

- 构建更慢；
- 动态特性受到限制；
- 不同操作系统和 CPU 架构通常需要分别构建；
- JVM 经过充分预热后，在部分长期高吞吐场景中可能更有优势。

## Spring AOP 在 Native 模式下的影响

Spring AOP 并非不能使用。主要区别：

```text
JVM：通常在运行时动态生成代理
Native：Spring AOT 尽量在构建期确定并生成代理
```

通常可支持：

- `@Transactional`；
- `@Cacheable`；
- `@Async`；
- 方法安全；
- 构建期可发现的 `@Aspect`；
- Spring 管理的 JDK/CGLIB Bean 代理。

需要特别注意：

1. 运行时才创建的 `ProxyFactory` 代理可能需要 `RuntimeHints`；
2. `Class.forName` 等动态加载可能需要反射提示；
3. CGLIB 不能代理 `final` 类、`final` 方法和 `private` 方法；
4. `this.method()` self-invocation 不经过代理，JVM 与 Native 都不会触发切面；
5. AspectJ Java Agent/Load-Time Weaving 不适合 Native Image；
6. 自定义代理和切面应通过 `nativeTest` 或原生集成测试验证。

JDK 动态代理提示示例：

```java
hints.proxies().registerJdkProxy(MyInterface.class);
```

判断原则：

> Spring 能否在构建阶段确定需要代理哪个 Bean、哪些接口和哪些方法。

## 本课完成证据

- [x] Spring Boot 4.1 项目可通过 JVM 测试；
- [x] Buildpacks 使用 Java 25；
- [x] Native OCI 镜像构建成功；
- [x] JVM OCI 镜像构建成功；
- [x] Native 与 JVM `/hello` 行为一致；
- [x] 对比了空闲内存和 PIDS；
- [x] 对比了镜像总大小和具体 layer；
- [x] 定位并解决 Docker Hub EOF；
- [x] 定位并解决 GitHub NIK 下载过慢/失败；
- [x] Builder 内部下载成功使用宿主机代理；
- [x] 理解 Native 不需要完整 JRE 的原因；
- [x] 理解 Spring AOP 在 AOT/Native 下的边界。

## 下一课

第 2 课：本地原生可执行文件与自动化原生测试。

目标：

1. 执行 `./gradlew nativeCompile`；
2. 检查 `build/native/nativeCompile/native-demo`；
3. 不依赖 Docker 直接运行原生程序；
4. 使用 `nativeTest` 在原生测试二进制中运行测试；
5. 比较本地 JVM、Native 可执行文件和 Native 容器三种运行方式；
6. 为下一阶段的反射、资源、代理和 `RuntimeHints` 故障实验建立测试基础。
