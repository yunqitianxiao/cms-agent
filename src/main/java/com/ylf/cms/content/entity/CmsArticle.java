package com.ylf.cms.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章实体（M2 内容模块内部，禁止被其他模块直接 import）。
 *
 * 字段与 src/main/resources/sql/schema.sql 的 cms_article 表一一对应（以 M1 建的表为准）：
 * status 落库是字符串枚举 DRAFT/PENDING/PUBLISHED/REJECTED/OFFLINE，
 * 由 ArticleStatusEnum 统一解释，避免各模块各维护一套数字码对照表。
 *
 * create_time/update_time/create_by/update_by 由 AuditMetaObjectHandler 统一填充，
 * 业务代码不手写时间与操作人。
 */
@Data
@TableName("cms_article")
public class CmsArticle {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标题 */
    private String title;

    /** 摘要 */
    private String summary;

    /** 正文 */
    private String content;

    /** 所属分类（cms_category.id） */
    private Long categoryId;

    /** 作者（sys_user.id） */
    private Long authorId;

    /** 状态：见 ArticleStatusEnum（DRAFT/PENDING/PUBLISHED/REJECTED/OFFLINE） */
    private String status;

    /** 最后审核人（sys_user.id） */
    private Long auditorId;

    /** 审核意见（驳回时必填） */
    private String auditOpinion;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 提交审核时间 */
    private LocalDateTime submitTime;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 下架原因 */
    private String offlineReason;

    /** 阅读量（前台展示用，M2 只累加不展示明细） */
    private Integer viewCount;

    /** 乐观锁版本号：并发编辑时由 MP OptimisticLockerInnerInterceptor 校验 */
    @Version
    private Integer version;

    /** 逻辑删除：0 正常 / 1 已删除（表上 deleted 列，避免物理删除丢数据） */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
}
