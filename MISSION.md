# Mission: 用 JDK 25 构建 Spring Boot 原生可执行文件

## Why
把一个 Spring Boot 4.x 服务编译成可部署的 GraalVM Native Image，在保留服务正确性的同时获得更快启动和更低运行时内存占用。

## Success looks like
- [x] 能用 Gradle 构建并直接运行本地原生可执行文件
- [x] 能验证 HTTP 接口在 JVM 与 Native Image 下行为一致
- [ ] 能定位 AOT、反射、资源和第三方依赖的可达性问题
- [x] 能判断何时选择本地 `nativeCompile`，何时选择 Buildpacks

## Constraints
- 使用 JDK 25
- 使用 Spring Boot 4.x 和 Gradle
- 全程使用中文，课程短小并以动手验证为主
- 明确标注 Spring Boot、Gradle 与 GraalVM 的版本兼容边界

## Out of scope
- GraalVM Polyglot、Truffle 和自定义语言实现
- 与完成 Spring Boot Native Image 无关的编译器内部原理
- 在第一个可用原生程序之前进行深度性能调优
