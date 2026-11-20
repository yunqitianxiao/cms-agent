package com.ylf.cms.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ylf.cms.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 文章列表查询条件。
 *
 * 分页参数直接继承 common 的 PageQuery，pageSize 上限 200 的校验只维护一处，
 * 各业务模块不再各写一套分页 DTO。
 *
 * categoryIds 是服务端内部字段：Controller 收到 categoryId 后，由 Service 查
 * 分类树把它展开成"分类 + 所有子分类"的 id 列表再放进这里，SQL 用 IN 一次筛完。
 * 之所以不暴露给前端，是不让调用方传一个跨父分类的任意 id 集合绕开分类树约束。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleQueryDTO extends PageQuery {

    /** 标题模糊匹配 */
    private String title;

    /** 状态：DRAFT/PENDING/PUBLISHED/REJECTED/OFFLINE，null 表示全部 */
    private String status;

    /** 分类 id（前端传），Service 会展开为 categoryIds */
    private Long categoryId;

    /** 只看某个作者的文章（"我的文章"入口用） */
    private Long authorId;

    /** 服务端内部使用：categoryId 展开后的 id 列表 */
    @JsonIgnore
    private List<Long> categoryIds;
}
