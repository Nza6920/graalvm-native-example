# 接口可达不代表 JDK 代理组合已注册

用户能够正确判断：`ProxyGreeting` 接口类型已经进入 Native Image，但运行时传给 `Proxy.newProxyInstance()` 的接口组合没有预先注册，因此代理类不存在。添加精确的 `registerJdkProxy(ProxyGreeting.class)` 后，原生测试由 4/5 通过变为 5/5 通过。

## Evidence

- 对根因检查题选择“代理组合没有预先注册”。
- JVM 端到端测试通过。
- 红灯 AOT metadata 包含接口类型，但没有 proxy 条目。
- 未注册 proxy hint 时，原生测试稳定复现 `MissingReflectionRegistrationError`。
- 添加 proxy 条目后，5 个原生测试全部通过。

## Implications

后续可以区分三类 metadata：reflection 让类型和成员可动态访问，resources 把文件字节加入镜像，proxies 为有序接口组合准备代理实现。多接口代理必须按照运行时接口数组的相同顺序注册。
