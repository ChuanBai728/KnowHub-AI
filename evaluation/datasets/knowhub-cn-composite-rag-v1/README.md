# KnowHub 中文复合 RAG 正式评测集 v1

当前版本为 `1.0.0-rc1`，包含 300 道中文评测题和 554 份最小化语料文档。前 250 题由公开数据集按固定规则转换并通过自动校验；最后 50 道技术文档题仍在人工复核中，完成签核后再升级为正式 `1.0.0`。

## 组成

| 组件 | 题数 | 文档数 | 主要评测目标 | 当前审核状态 |
| --- | ---: | ---: | --- | --- |
| T2Retrieval | 150 | 279 | 召回、融合检索、RRF、Rerank | 自动转换 |
| CMRC 2018 | 50 | 25 | 中文抽取式问答、答案事实、引用锚点 | 自动转换 |
| RGB | 50 | 240 | 噪声鲁棒性、拒答、信息整合、反事实鲁棒性 | 自动转换 |
| Kubernetes / Apache Dubbo 中文文档 | 50 | 10 | 章节导航、步骤问答、多轮追问、图谱路由、澄清 | 待人工复核 |

## 文件

- `dataset.json`：版本、数量、题型分布和审核进度。
- `gold.jsonl`：逐题金标；每行一个完整测试用例。
- `manifest.json`：语料清单、来源修订号、许可和 SHA-256。
- `corpus/`：只包含 300 题实际用到的材料，不导入原始全量语料。
- `sources.json`：确定性抽样规则和固定来源版本。
- `schema.json`：`gold.jsonl` 单条记录的 JSON Schema。
- `quality-report.json`：严格校验器最近一次输出。
- `THIRD_PARTY_NOTICES.md`：第三方来源、修改和许可说明。

技术文档题的可编辑源文件位于同级上层的 `knowhub-cn-composite-rag-v1.technical-doc-cases.jsonl`。不要直接修改生成后的 `gold.jsonl`；人工审核结果应写回该源文件后重新构建。

## 构建和校验

首次构建需要访问已固定版本的上游数据：

```powershell
python -B -m evaluation.tools.build_composite_eval
```

下载缓存就绪后可完全离线重建：

```powershell
python -B -m evaluation.tools.build_composite_eval --offline
python -B -m evaluation.tools.validate_composite_eval --report evaluation/datasets/knowhub-cn-composite-rag-v1/quality-report.json
python -B -m unittest discover evaluation/tools
```

构建器不会清除本目录中的说明、许可和 schema 文件，只会重写生成数据与 `corpus/`。

## 评分建议

- 检索：Recall@5、Recall@10、MRR@10、nDCG@10，并按 T2Retrieval 单独报告。
- 答案：答案事实覆盖率、答案正确性、必需引用命中率、引用忠实度。
- 鲁棒性：噪声前后正确率差值、应拒答准确率、反事实跟随率（越低越好）。
- 路由：执行模式准确率；技术文档题另报 `GRAPH_ONLY`、`GRAPH_THEN_EVIDENCE`、`CLARIFICATION` 的分类结果。
- 多轮：在保留 `conversation` 历史的情况下评分，不可退化为孤立单轮问题。

所有总分都应同时给出四个组件的分项结果，避免题量最大的检索部分掩盖拒答或路由缺陷。

## 使用限制

本集合是混合许可集合。RGB 数据采用 `CC-BY-NC-SA-4.0`，因此当前复合集合明确标记为不可商用。分发、改编或发布结果前请阅读 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
