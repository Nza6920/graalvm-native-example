# 第 2 课：本地 nativeCompile 与 nativeTest

日期：2026-07-23

## 本课目标

- 不使用 Docker，直接用本机 GraalVM 构建原生可执行文件；
- 检查原生文件类型、大小和动态依赖；
- 启动原生 Spring Boot 服务并验证 `/hello`；
- 把 JUnit 与 Spring Test 编译为原生测试程序；
- 理解 `test`、`nativeCompile`、`nativeRun` 和 `nativeTest` 的区别。

## 本机环境

| 项目 | 实测 |
|---|---|
| Java | GraalVM CE 25.0.2 |
| `native-image` | 25.0.2 |
| SubstrateVM GC | Serial GC |
| Gradle | 9.5.1 |
| Gradle Launcher JVM | GraalVM CE 25.0.2 |
| Gradle Daemon JVM | SDKMAN GraalVM CE 25.0.2 |
| 操作系统 | WSL2 Linux amd64 |

本地构建和上一课 Buildpacks 构建使用不同发行环境：

| 路径 | Native Image 环境 |
|---|---|
| 本地 `nativeCompile` | GraalVM CE 25.0.2 |
| Buildpacks `bootBuildImage` | BellSoft Liberica NIK 25.0.3 |

它们都基于 JDK 25，但发行版和补丁版本不同。因此定位问题时要先区分“本机构建”还是“builder 容器构建”。

## Spring AOT 与 GraalVM AOT

“AOT”在 Spring Boot Native 中有两个不同层次。

### Spring AOT

Spring AOT 负责把 Spring Framework 的动态行为提前展开：

- 分析 Bean 定义和自动配置；
- 生成 Bean 注册代码；
- 提前生成或描述代理；
- 生成反射、资源、序列化和 JNI hints；
- 为 GraalVM 提供更容易静态分析的应用结构。

对应的 Gradle 任务：

```text
processAot
compileAotJava
processAotResources
aotClasses
```

### GraalVM AOT

GraalVM Native Image 负责把 Java 字节码和 Spring AOT 生成物编译为目标平台机器码：

```text
.class 字节码
    ↓ native-image
Linux 原生可执行文件
```

对应任务：

```text
nativeCompile
```

完整流水线：

```text
Java 源码
   ↓ javac
Java 字节码
   ↓
Spring AOT
   ├── Bean 注册代码
   ├── Proxy 信息或代码
   ├── Reflection hints
   └── Resource metadata
   ↓
GraalVM Native Image AOT
   ↓
原生可执行文件
```

在本项目中应用 `org.graalvm.buildtools.native` 后，Spring Boot 会自动启用 Spring AOT，并把 AOT 输出加入应用和测试 Native binary 的 classpath。因此执行：

```bash
./gradlew nativeCompile
```

会自动触发 Spring AOT，不需要手工先运行 `processAot`。

普通 Java Native 应用不需要 Spring AOT；Spring Boot Native 通常需要两者配合，因为 GraalVM 本身不了解 Spring Bean、自动配置、`@Transactional` 或 Spring AOP。

Spring AOT 也可以脱离 Native Image，在 JVM 上运行：

```bash
./gradlew bootJar
java -Dspring.aot.enabled=true \
  -jar build/libs/native-demo-0.0.1-SNAPSHOT.jar
```

这仍然需要 JRE，但可以更快验证：

- Spring AOT 初始化是否正确；
- Bean 图和条件配置是否兼容；
- 代理和 AOP 配置是否能在 AOT 模式下建立；
- 问题发生在 Spring AOT 阶段还是 GraalVM 原生编译/运行阶段。

推荐反馈顺序：

```text
JVM test
   ↓
JVM + Spring AOT
   ↓
nativeTest
   ↓
nativeCompile / bootBuildImage
```

一句话总结：

> Spring AOT 解决“Spring 太动态”的问题，GraalVM AOT 解决“把 Java 编译为机器码”的问题。

## JVM 测试基线

命令：

```bash
./gradlew test
```

结果：

```text
BUILD SUCCESSFUL
```

先建立 JVM 测试基线的原因：

> 如果普通测试已经失败，就不能把后续失败归因于 Native Image、AOT 或 reachability metadata。

## 本地原生编译

命令：

```bash
./gradlew nativeCompile
```

Native Build Tools 从 `JAVA_HOME` 找到：

```text
/home/ncw/.sdkman/candidates/java/25.0.2-graalce/lib/svm/bin/native-image
```

这次构建不使用 Docker，也不下载 Buildpacks 的 BellSoft NIK。

### 编译环境

```text
Java version: 25.0.2
Target machine: x86-64-v3
C compiler: gcc 13.3.0
Garbage collector: Serial GC
Build memory budget: 6.06 GB
Build threads: 16
```

`x86-64-v3` 表示生成的程序要求目标 CPU 支持对应指令集。进一步使用 `-march=native` 可能提升当前机器性能，但会降低跨机器可移植性。

### Closed-world analysis

实测：

```text
18,546 types reachable
27,397 fields reachable
86,152 methods reachable

6,852 types registered for reflection
3,230 fields registered for reflection
13,816 methods registered for reflection

68 types registered for JNI
4 native libraries: dl, pthread, rt, z
```

这里体现了 Native Image 的闭世界分析：

- 从入口点分析所有可达代码；
- 通过 Spring AOT 和 reachability metadata 补充反射、资源、代理和 JNI；
- 没有被证明可达或显式注册的内容可以从最终程序中移除。

### 编译阶段

```text
1. Initializing
2. Performing analysis
3. Building universe
4. Parsing methods
5. Inlining methods
6. Compiling methods
7. Laying out methods
8. Creating image
```

其中静态分析和机器码编译耗时最长。本次机器码编译阶段约 152 秒。

### 构建结果

```text
Native Image generation: 6m 14s
Gradle nativeCompile:    6m 49s
Peak RSS:                5.15 GB
GC time:                 35.4s
GC count:                607
```

最终镜像组成：

| 内容 | 大小 | 比例 |
|---|---:|---:|
| Code area | 44.56 MB | 48.27% |
| Image heap | 39.12 MB | 42.38% |
| Other data | 8.63 MB | 9.35% |
| Total image size | 92.31 MB | 100% |
| Executable file size | 87.49 MB | — |

代码区主要来源包括：

- `java.base`；
- Tomcat 11；
- `java.xml`；
- SubstrateVM；
- Jackson；
- Spring Core、Boot、Beans、Web 和 Web MVC。

Image heap 包括：

- 代码和反射元数据；
- Java 字符串；
- `java.lang.Class`；
- 预计算对象；
- 嵌入资源。

## 输出文件

目录：

```text
build/native/nativeCompile/
```

主要文件：

```text
native-demo
libawt.so
libawt_headless.so
libawt_xawt.so
libjava.so
libjvm.so
libmanagement_ext.so
```

主程序：

```text
build/native/nativeCompile/native-demo
```

文件检查：

```bash
file build/native/nativeCompile/native-demo
```

结果：

```text
ELF 64-bit LSB pie executable
x86-64
dynamically linked
stripped
```

直接动态依赖：

```bash
ldd build/native/nativeCompile/native-demo
```

结果包括：

```text
libz.so.1
libc.so.6
/lib64/ld-linux-x86-64.so.2
```

结论：

- Java 应用和必要运行时已经 AOT 编译进原生程序；
- “Native”不必然意味着“完全静态链接”或“绝对单文件”；
- 程序仍可能依赖操作系统 ABI 和少量动态库；
- 部署整个本地构建结果前，应在目标 Linux 环境验证动态库和 CPU 指令集兼容性。

## 运行本地原生服务

为避免和已有 Docker 容器冲突，使用 8082：

```bash
./build/native/nativeCompile/native-demo --server.port=8082
```

启动日志：

```text
Starting AOT-processed NativeDemoApplication using Java 25.0.2
Root WebApplicationContext: initialization completed in 56 ms
Started NativeDemoApplication in 0.142 seconds
process running for 0.156 seconds
```

日志中的“using Java 25.0.2”表示这个原生程序的构建/runtime 元数据，不表示启动了完整 HotSpot JVM。

验证接口：

```bash
curl http://127.0.0.1:8082/hello
```

结果：

```text
Hello from GraalVM!
```

进程实测：

| 指标 | 数值 |
|---|---:|
| RSS | 95,920 KiB |
| Threads/PIDS | 18 |
| CPU（空闲瞬时值） | 0.7% |

本地 `ps` 的 RSS 与 Docker `stats` 的 cgroup 内存口径不同，不能直接把两组数字当作完全相同的指标比较。

停止时 Spring Boot 完成 Tomcat graceful shutdown。

## nativeTest 的工作方式

命令：

```bash
./gradlew nativeTest
```

它不是把 JVM 测试简单地指向应用可执行文件，而是：

1. 先运行 JVM `test`，发现要执行的测试；
2. 执行 Spring Test AOT；
3. 生成测试资源配置；
4. 将应用、Spring Test、JUnit Platform 和测试代码编译成独立原生测试程序；
5. 运行原生测试程序。

生成：

```text
build/native/nativeTestCompile/native-demo-tests
```

加载的 Native Image feature：

```text
GsonFeature
JUnitPlatformFeature
PreComputeFieldFeature
```

### 原生测试程序分析结果

```text
22,174 types reachable
30,658 fields reachable
96,960 methods reachable

7,996 types registered for reflection
3,757 fields registered for reflection
15,817 methods registered for reflection
```

测试程序比应用程序包含更多可达代码，因为还需要 JUnit Platform、Spring Test、MockMvc 和相关元数据。

### 原生测试程序构建结果

```text
Native test image generation: 5m 12s
Gradle nativeTest:           5m 58s
Peak RSS:                    4.88 GB
Executable file size:        96.73 MB
```

组成：

| 内容 | 大小 |
|---|---:|
| Code area | 48.76 MB |
| Image heap | 43.52 MB |
| Other data | 9.92 MB |

### 测试执行结果

```text
GreetingControllerTests > returnsGreeting() SUCCESSFUL
NativeDemoApplicationTests > contextLoads() SUCCESSFUL
```

JUnit Native 汇总：

```text
2 tests found
2 tests started
2 tests successful
0 tests failed
Test run finished after 135 ms
```

这证明：

- Spring 测试上下文可以在 Native Image 中启动；
- MockMvc 控制器测试在原生测试程序中通过；
- `/hello` 行为不仅在手工运行时正确，也有原生自动化测试覆盖。

## test、nativeCompile、nativeRun、nativeTest

| 任务 | 用途 | 是否生成原生程序 |
|---|---|---|
| `test` | JVM 上运行测试，建立快速基线 | 否 |
| `nativeCompile` | 编译应用原生可执行文件 | 是 |
| `nativeRun` | 构建并运行应用原生可执行文件 | 是 |
| `nativeTestCompile` | 编译原生测试可执行文件 | 是 |
| `nativeTest` | 编译并运行原生测试 | 是 |

推荐开发反馈顺序：

```text
快速、多次运行 test
        ↓
关键变更运行 nativeTest
        ↓
交付前运行 nativeCompile / bootBuildImage
```

不要用每次 5～7 分钟的 `nativeTest` 代替日常秒级 JVM 单元测试。

## 本课观察到的非致命警告

构建分析阶段出现：

```text
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
```

本次它只出现在 Native Image 构建期分析进程中：

- 没有导致 `nativeCompile` 或 `nativeTest` 失败；
- 最终原生应用运行时 Logback 日志正常；
- 因此记录为构建期非致命警告，不在本课做额外修改。

## 本课完成证据

- [x] 本机使用 GraalVM CE JDK 25；
- [x] JVM 测试基线通过；
- [x] `nativeCompile` 成功；
- [x] 检查 ELF 文件类型和动态依赖；
- [x] 本地 Native 服务在 0.142 秒内启动；
- [x] `/hello` 返回 `Hello from GraalVM!`；
- [x] 本地 Native 服务 graceful shutdown；
- [x] `nativeTestCompile` 成功；
- [x] `nativeTest` 中 2/2 测试通过；
- [x] 理解应用原生程序和测试原生程序的区别。

## 下一课

第 3 课：故意制造并修复 reachability 问题。

计划：

1. 添加一个通过配置字符串和反射加载的类型；
2. 先让 JVM 测试通过；
3. 观察 Native Image 构建或运行失败；
4. 使用 `RuntimeHintsRegistrar` 注册反射提示；
5. 添加资源文件并验证资源提示；
6. 添加一个动态代理场景；
7. 使用 `nativeTest` 锁定修复；
8. 区分 Spring AOT 自动推断和业务自定义 hints。
