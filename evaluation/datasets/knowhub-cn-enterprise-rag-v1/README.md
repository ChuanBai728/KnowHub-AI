# KnowHub 中文企业知识库 RAG 评测集 v1

这是为 KnowHub AI 定制的首版中文评测集。它不是现实公司的制度文件，所有名称、规则、金额、日期、产品和事件均为虚构测试数据，不应被当作真实业务建议。

## 内容

- `corpus/`：10 份可直接上传到 KnowHub 的 Markdown 文档。
- `manifest.json`：稳定文档键、版本、知识范围和上传元数据。
- `gold.jsonl`：68 条评测样本，其中 Smoke 20 条、Regression 30 条、Challenge 18 条。
- `schema.json`：Gold 样本字段约束。

数据集刻意包含：

- 两个版本的同一产品手册，用于测试版本冲突与最新版本选择。
- 容易混淆的金额、时限、等级和审批阈值。
- 章节、父子关系和编号步骤，用于测试结构图路径。
- 跨人事、财务、采购、IT、安全和产品域的问题。
- 无答案、错误前提、模糊指代和需澄清问题。

## 与现有执行器的兼容性

`manifest.json` 和 `gold.jsonl` 保留了现有 `enterprise_rag_bench_server_eval.py` 所需字段：

- 文档：`doc_id`、`source_type`、`title`、`path`
- 问题：`question_id`、`question_type`、`source_types`、`question`、`expected_doc_ids`、`gold_answer`、`answer_facts`

扩展字段用于路由、生成与行为评分。执行器会按样本读取 `chat_mode`，在 `DOCUMENT` 模式下把
`selected_document_key` 映射为运行时文档 ID，并通过真实接口回放 `conversation` 中最后一问之前的用户轮次。
因此多轮样本和 `OPEN_CHAT` 工具样本也能走正确的产品路径；工具调用是否发生仍需结合 trace 单独评分。

在服务已启动并配置管理员密码后，可以跳过外部数据准备阶段。先复制一份可写运行目录，避免状态文件污染基准数据：

```bash
cp -R evaluation/datasets/knowhub-cn-enterprise-rag-v1 \
  evaluation/reports/knowhub-cn-enterprise-rag-v1-local

python evaluation/tools/enterprise_rag_bench_server_eval.py \
  --output-dir evaluation/reports/knowhub-cn-enterprise-rag-v1-local \
  --phase import
```

不要直接在本数据目录执行 `evaluate`：执行器会把运行状态、逐题观测和报告写进 `output-dir`。
应将数据目录作为只读基线，把运行产物写到独立的 `evaluation/reports/`。

## 标注约定

- `expected_doc_ids` 是文档级 Gold，使用稳定键，不使用运行时数据库 ID。
- `gold_evidence` 使用章节路径和短文本锚点定位证据。
- `answer_facts` 是答案必须覆盖的事实，不要求逐字匹配 `gold_answer`。
- `allowed_execution_modes` 允许多个同样合理的执行路径。
- `should_abstain` 表示知识库没有足够证据，应明确说明资料不足。
- `should_clarify` 表示在回答前需要用户补充文档、版本、对象或范围。
- `forbidden_claims` 是回答中绝不能出现的断言。
- 多轮样本的 `question` 是最后一轮问题，`conversation` 保存完整上下文。

## 使用建议

1. 导入 `manifest.json` 中全部10份文档。旧版产品手册的 `active=false` 表示业务状态为历史版本，但仍需导入以测试版本冲突处理。
2. PR 回归运行 `split=smoke`，完整回归运行全部68条。
3. 检索评分以 `expected_doc_ids` 和 `gold_evidence` 为主。
4. 生成评分同时检查 `answer_facts`、引用支持、`should_abstain` 和 `forbidden_claims`。
5. 每次修改文档后先校验所有 `text_anchor` 仍能在对应文档中找到。

## 许可证

本数据集为专门生成的合成测试数据，随 KnowHub AI 项目按 Apache-2.0 使用。第三方公开数据集不包含在本目录中。
