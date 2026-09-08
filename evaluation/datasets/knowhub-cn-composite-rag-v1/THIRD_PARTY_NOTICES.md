# 第三方数据与许可说明

本评测集对以下材料进行了抽样、裁剪、编号统一、证据锚点提取及 KnowHub 评测字段转换。每份材料的精确上游版本、来源 URL 和许可也记录在 `sources.json` 与 `manifest.json`。

## T2Retrieval

- 来源：`mteb/T2Retrieval`
- 固定修订：`921dd3af6e78d1ae7ee0368aa8d7eaee02c8f08e`
- 许可：Apache License 2.0
- 改造：选择前 150 个存在相关性标注的非空查询；每题最多物化两个正例文档。

## CMRC 2018

- 来源：`ymcui/cmrc2018`
- 固定修订：`c0eb1b6ba219847457e6af3180da722bbeb656af`
- 许可：CC BY-SA 4.0
- 改造：从公开开发集均匀选取 25 个合格段落，每段取前两题，生成 50 道引用感知问答题。
- 论文：Cui et al., *A Span-Extraction Dataset for Chinese Machine Reading Comprehension*, EMNLP-IJCNLP 2019。

## RGB

- 来源：`chen700564/RGB`
- 固定修订：`65ec39e40e7dc9abb50e9bf1b4f32be3f6f16615`
- 许可：CC BY-NC-SA 4.0
- 改造：选择 20 道噪声题、10 道负例拒答题、10 道信息整合题和 10 道反事实题；过滤乱码和中文内容不足的段落。
- 重要限制：该许可包含非商业条款，因此包含 RGB 的当前复合集合仅限非商业使用。

## Kubernetes 中文文档

- 来源：`kubernetes/website`
- 固定修订：`3203edad9a1f98f4fd5a36c03512d9c73546c4e6`
- 许可：CC BY 4.0
- 改造：从固定版本的 5 篇中文文档提取题目所需正文，并创建章节导航、步骤问答、多轮和澄清题。

## Apache Dubbo 中文文档

- 来源：`apache/dubbo-website`
- 固定修订：`84b35ebdab20a54da2c740aa8428a375ff43b481`
- 许可：Apache License 2.0
- 改造：从固定版本的 5 篇中文文档提取题目所需正文，并创建章节导航、步骤问答、多轮和澄清题。

本文件是数据来源与限制记录，不替代各上游项目随附的完整许可证文本。再分发时应保留上游署名、链接、修订号以及适用的相同方式共享或非商业限制。
