# Spring Boot GraalVM Native Image 学习资源

## Knowledge

- [Spring Boot 4.1 系统要求](https://docs.spring.io/spring-boot/4.1/system-requirements.html)
  核对 Spring Boot 4.1、Java、Gradle 和 Native Build Tools 的官方兼容范围。
- [Spring Boot 4.1：第一个 GraalVM 原生应用](https://docs.spring.io/spring-boot/4.1/how-to/native-image/developing-your-first-application.html)
  Spring 官方的 Gradle、`nativeCompile`、Buildpacks 和运行流程。
- [Spring Boot 4.1：理解 GraalVM Native Image](https://docs.spring.io/spring-boot/4.1/reference/packaging/native-image/introducing-graalvm-native-images.html)
  理解 Spring AOT、闭世界假设和运行时提示。
- [GraalVM JDK 25 Native Image](https://www.graalvm.org/jdk25/reference-manual/native-image/)
  JDK 25 对应的 Native Image 构建、系统依赖与可达性元数据参考。
- [Gradle Java 兼容矩阵](https://docs.gradle.org/current/userguide/compatibility.html)
  确认哪个 Gradle 版本可以使用 JDK 25 运行或作为工具链。
- [GraalVM Native Build Tools Gradle 指南](https://graalvm.github.io/native-build-tools/latest/end-to-end-gradle-guide.html)
  `nativeCompile`、`nativeRun`、`nativeTest` 和故障诊断的官方指南。
- [Spring Boot 4.1：使用 Gradle 打包 OCI 镜像](https://docs.spring.io/spring-boot/4.1/gradle-plugin/packaging-oci-image.html)
  `bootBuildImage`、builder 环境变量、代理、镜像名称、pull policy 和缓存配置。
- [Paketo Java Buildpacks How-To](https://paketo.io/docs/howto/java/)
  `BP_JVM_VERSION`、JRE/JDK 选择、Native Image 和 Buildpack 下载行为。
- [Spring Framework 7：AOP 代理机制](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
  JDK 动态代理、CGLIB、self-invocation、`final` 方法和类代理限制。
- [Spring Framework 7：AOT 官方文档](https://docs.spring.io/spring-framework/reference/core/aot.html)
  Spring AOT、runtime hints 和 Native Image 可达性问题的框架级说明。
- [Spring Framework：`@ImportRuntimeHints` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/ImportRuntimeHints.html)
  导入 `RuntimeHintsRegistrar` 的当前 API 与条件注册语义。
- [Spring Framework：`ResourceHints` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aot/hint/ResourceHints.html)
  注册 Native Image 运行时需要的 classpath 资源模式。
- [Spring Framework：`ProxyHints` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aot/hint/ProxyHints.html)
  注册 Native Image 构建期需要准备的有序 JDK 代理接口组合。

## Wisdom (Communities)

- [GraalVM Community](https://www.graalvm.org/community/)
  可在官方 Slack 向 GraalVM 团队和实践者询问可复现的 Native Image 问题。
- [Spring Boot Community](https://docs.spring.io/spring-boot/community.html)
  使用 Stack Overflow 的 `spring-boot` 标签提问；可复现的缺陷提交到 Spring Boot GitHub。
