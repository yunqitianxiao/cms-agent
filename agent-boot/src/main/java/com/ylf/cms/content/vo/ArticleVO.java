package com.ylf.cms.content.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章 VO（M2 对外数据契约，供 M3 前台/工作台展示使用）。
 *
 * 只放展示需要的字段：不出现 deleted/create_by/update_by 这类内部列，
 * 实体字段变化不影响前台接口契约。
 */
@Data
public class ArticleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String summary;

    private String content;

    private Long authorId;

    /** 作者昵称（联查 sys_user 得出；不返回账号名，避免泄露登录名） */
    private String authorName;

    private Long categoryId;

    /** 分类名称（联查 cms_category 得出） */
    private String categoryName;

    /** 状态码：DRAFT/PENDING/PUBLISHED/REJECTED/OFFLINE */
    private String status;

    /** 状态中文描述 */
    private String statusDesc;

    /** 乐观锁版本号：编辑时回传，CAS 冲突返回 40900 */
    private Integer version;

    private Integer viewCount;

    private LocalDateTime submitTime;

    private LocalDateTime publishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
