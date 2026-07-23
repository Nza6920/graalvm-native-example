# 源码存在不代表 Native Image 可达

用户能够正确判断：配置字符串引用的类虽然存在于源码和 JVM classpath 中，但没有静态调用边或 runtime hint 时，Native Image 仍可能把它当作不可达代码移除。使用精确的 `RuntimeHintsRegistrar` 注册无参构造器和 `message()` 方法后，原生测试由 2/3 通过变为 3/3 通过。

## Evidence

- 对根因检查题选择“构建阶段缺少可达信息”。
- JVM 端到端测试通过。
- 未注册 hints 时，原生测试稳定复现 `ClassNotFoundException`。
- 注册 `ExecutableMode.INVOKE` 后，3 个原生测试全部通过。

## Implications

后续可以使用同一诊断框架学习 classpath 资源和动态代理：先建立 JVM 绿灯，再复现 Native 红灯，最后添加最小范围的 hint。
