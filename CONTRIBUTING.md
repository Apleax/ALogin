# 贡献指南

感谢参与 ALogin。本文说明如何构建、测试与提交改动。

## 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 25 |
| Maven | 3.9 及以上 |

## 构建与测试

```bash
# 完整构建
mvn clean package

# 构建可独立测试的模块并运行单元测试
mvn -pl entity,core,server,app -am clean test
```

`geyser-extension` 与 `viaproxy-plugin` 依赖外部快照仓库和需要通过 `mvn install:install-file` 安装的第三方 jar，默认环境无法直接构建，CI 也不包含这两个模块。改动它们时请自行在对应环境中验证。

## 模块说明

| 模块 | 职责 |
|---|---|
| `entity` | 实体、VO/BO/PO、枚举、错误码与业务异常 |
| `core` | 业务服务、SQL 映射、配置与工具类 |
| `server` | 基于 Minestom 的内置登录服 |
| `app` | HTTP 入口、控制器与全局异常处理 |
| `geyser-extension` | Geyser 侧的基岩版账号对接扩展 |
| `viaproxy-plugin` | ViaProxy 侧的正版会话校验插件 |

## 提交改动

1. 从最新 `master` 拉出分支，命名建议 `feat/…`、`fix/…`、`refactor/…`
2. 改动保持聚焦，不要顺带重排无关代码或调整无关格式
3. 本地测试通过后再提交
4. 提交 PR，说明改了什么、影响范围、如何验证

`master` 保持线性历史，PR 统一使用 **Squash and merge**，合并后删除源分支。

## 提交信息

格式：`type: 摘要`

`feat` 新功能 · `fix` 缺陷修复 · `refactor` 重构 · `perf` 性能 · `test` 测试 · `docs` 文档 · `chore` 构建与杂项

## 代码约定

- 与现有代码风格保持一致，注释只写必要信息
- 行为变更需附带或更新单元测试
- 不提交调试代码、临时脚本与个人配置

## 版本与发布（维护者）

- 版本号只在根 `pom.xml` 的 `<revision>` 维护，其余模块与资源均引用 `${revision}`
- 遵循语义化版本：新增对外可见的接口契约或字段为 minor，缺陷修复为 patch
- 发布提交使用 `chore(release): 版本号定为 X.Y.Z`，并在 `master` 的该提交上打 `vX.Y.Z` 标签
