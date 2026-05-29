package com.baidu.fsg.uid.worker.entity;

import java.util.Date;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import com.baidu.fsg.uid.worker.WorkerNodeType;

/**
 * 工作节点实体类 - 存储工作节点的信息
 *
 * 【类的作用】
 * 这个类是一个实体类（Entity），用于存储工作节点的详细信息。
 * 在原始的百度UID生成器中，Worker ID通常是通过数据库自增的方式分配的，
 * 每个节点会在数据库中插入一条记录，数据库自增的ID就作为Worker ID。
 *
 * 【字段说明】
 * - id：节点ID（通常由数据库自增生成）
 * - hostName：节点的主机名
 * - port：节点的端口号
 * - type：节点类型（容器或实际机器）
 * - launchDate：节点启动时间
 * - created：记录创建时间
 * - modified：记录最后修改时间
 *
 * 【设计模式】
 * 使用了"实体类"模式：
 * - 对应数据库中的一张表
 * - 提供getter/setter方法访问属性
 * - 重写toString()方法便于调试
 *
 * 【当前使用情况】
 * 在本项目中，Worker ID是通过Redis分配的（RedisDisposableWorkerIdAssigner），
 * 所以这个实体类主要用于参考和扩展，实际代码中可能未直接使用。
 *
 * 【数据库表结构】（参考）
 * CREATE TABLE worker_node (
 *     id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *     host_name VARCHAR(64),
 *     port VARCHAR(64),
 *     type INT,
 *     launch_date DATETIME,
 *     created DATETIME,
 *     modified DATETIME
 * );
 */
public class WorkerNodeEntity {

    /**
     * 节点ID
     * 通常由数据库自增生成，作为Worker ID使用
     */
    private long id;

    /**
     * 节点的主机名
     * 例如："server-01"、"docker-container-abc"
     */
    private String hostName;

    /**
     * 节点的端口号
     * 例如："8080"、"8443"
     */
    private String port;

    /**
     * 节点类型
     * 1-容器类型（CONTAINER）
     * 2-实际类型（ACTUAL）
     *
     * @see WorkerNodeType
     */
    private int type;

    /**
     * 节点启动时间
     * 默认为当前时间
     */
    private Date launchDate = new Date();

    /**
     * 记录创建时间
     * 由数据库自动设置
     */
    private Date created;

    /**
     * 记录最后修改时间
     * 由数据库自动更新
     */
    private Date modified;

    /**
     * 获取节点ID
     */
    public long getId() {
        return id;
    }

    /**
     * 设置节点ID
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * 获取主机名
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * 设置主机名
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * 获取端口号
     */
    public String getPort() {
        return port;
    }

    /**
     * 设置端口号
     */
    public void setPort(String port) {
        this.port = port;
    }

    /**
     * 获取节点类型
     */
    public int getType() {
        return type;
    }

    /**
     * 设置节点类型
     */
    public void setType(int type) {
        this.type = type;
    }

    /**
     * 获取启动时间
     */
    public Date getLaunchDate() {
        return launchDate;
    }

    /**
     * 设置启动时间
     */
    public void setLaunchDateDate(Date launchDate) {
        this.launchDate = launchDate;
    }

    /**
     * 获取创建时间
     */
    public Date getCreated() {
        return created;
    }

    /**
     * 设置创建时间
     */
    public void setCreated(Date created) {
        this.created = created;
    }

    /**
     * 获取修改时间
     */
    public Date getModified() {
        return modified;
    }

    /**
     * 设置修改时间
     */
    public void setModified(Date modified) {
        this.modified = modified;
    }

    /**
     * 将实体对象转换为字符串表示
     *
     * @return 包含所有字段值的字符串，便于调试和日志记录
     *
     * 【实现说明】
     * 使用Apache Commons Lang的ReflectionToStringBuilder
     * 通过反射自动获取所有字段并格式化输出
     */
    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

}
