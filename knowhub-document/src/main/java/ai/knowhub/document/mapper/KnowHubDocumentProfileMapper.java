package ai.knowhub.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import ai.knowhub.document.data.KnowHubDocumentProfile;

/**
 * 文档画像（Document Profile）Mapper接口 —— 数据访问层
 *
 * 作用：提供对knowhub_document_profile表的数据库操作能力。
 * 文档画像是系统利用AI（大语言模型）对文档内容进行分析后自动生成的结构化摘要，
 * 通常包含文档的主题、关键信息、适用场景等。
 *
 * 文档画像的用途：
 * 
 *   帮助用户快速了解文档内容概要，无需阅读全文
 *   辅助RAG检索时的文档匹配和排序
 *   作为知识路由的参考信息
 * 设计模式：Mapper层（DAO模式），继承BaseMapper获得通用CRUD能力。
 *
 * 对应的数据实体类：KnowHubDocumentProfile
 */
@Mapper  // MyBatis注解：标记为Mapper接口
public interface KnowHubDocumentProfileMapper extends BaseMapper<KnowHubDocumentProfile> {
    // 继承BaseMapper已提供了完整的CRUD方法
}
