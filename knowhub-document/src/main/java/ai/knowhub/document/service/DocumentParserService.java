package ai.knowhub.document.service;

import ai.knowhub.enums.DocumentFileTypeEnum;
import ai.knowhub.document.support.DocumentAnalysisResult;

/**
 * 【文档解析服务接口】
 *
 * 作用：负责将原始文档文件解析为结构化的分析结果。
 * 是文档处理流水线的第一个核心环节，将各种格式的文档统一转换为可处理的文本和元数据。
 *
 * 架构角色：
 *   - 属于文档处理流水线的"解析层"
 *   - 接收原始文件字节流，输出结构化的文档分析结果
 *   - 支持多种文档格式（PDF、Word、Markdown、HTML 等）
 *
 * 核心概念：
 *   - 文档解析（Document Parsing）：从原始文件中提取文本内容和结构信息
 *   - MIME 类型：文件的媒体类型标识，如 "application/pdf"、"text/markdown"
 *   - 文件类型枚举（DocumentFileTypeEnum）：系统内部定义的文件类型分类
 *   - 分析结果（DocumentAnalysisResult）：解析后的结构化输出，包含文本、元数据、结构信息等
 *
 * 设计模式：策略模式（Strategy Pattern）
 *   - 不同的文件类型可以有不同的解析策略
 *   - 调用方无需关心具体解析逻辑，只需传入文件和类型信息
 */
public interface DocumentParserService {

    /**
     * 解析文档文件
     *
     * 功能说明：
     *   - 读取文件的原始字节内容
     *   - 根据文件类型选择合适的解析器（如 Apache Tika）
     *   - 提取纯文本内容、文档元数据（作者、标题、创建时间等）
     *   - 识别文档的结构信息（章节、段落等）
     *   - 返回统一的分析结果对象
     *
     * @param bytes           文件的原始字节内容
     * @param originalFileName 原始文件名（如 "技术文档.pdf"），用于辅助类型判断
     * @param mimeType        文件的 MIME 类型（如 "application/pdf"），用于精确类型识别
     * @param fileType        系统内部的文件类型枚举，明确指定文件格式
     * @return 文档分析结果，包含解析后的文本、元数据和结构信息
     */
    DocumentAnalysisResult parse(byte[] bytes, String originalFileName, String mimeType, DocumentFileTypeEnum fileType);
}
