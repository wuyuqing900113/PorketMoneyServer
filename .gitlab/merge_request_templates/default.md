## 变更说明

关联 Issue：#

<!-- 本次变更的目的与方案概述 -->

## 测试情况

- [ ] 单元测试与功能代码同提交（mission.md：不允许先欠测试后补）
- [ ] 本地 `mvn clean verify` 全绿（含覆盖率 / Checkstyle / PMD / SpotBugs 门禁）
- [ ] 涉及 API 变更：已通过 Swagger UI 自测

## 自查清单

- [ ] 符合 `code-style-guide.md`（命名 / 分层 / DO-DTO-VO / 无魔法值 / 无空 catch）
- [ ] 无硬编码敏感信息；全部外部输入已校验；SQL 使用 `#{}` 参数化
- [ ] 新增错误码已在模块错误码枚举中集中定义（6 位数字分段码）
- [ ] API 变更已同步文档描述（SpringDoc 注解）
- [ ] 无跨模块直接依赖（业务模块仅经对方 service 层协作）
