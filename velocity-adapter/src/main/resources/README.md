# ALogin Velocity Identity Adapter

本适配器依赖 LibreLogin 的公开 API，只监听其 `AuthenticatedEvent.PREMIUM` 事件，并把 Java Profile UUID 写入短期 ALogin Cookie 断言。

启用前请在 `plugins/alogin-velocity-identity/config.properties` 设置 `java-shared-secret`，并在 ALogin 的 `identity.java-shared-secret` 设置相同值。两处密钥至少 32 个 UTF-8 字节；不要提交到 Git。
