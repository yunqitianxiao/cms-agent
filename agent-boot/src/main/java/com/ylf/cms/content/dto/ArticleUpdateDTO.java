package com.ylf.cms.content.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

/**
 * 文章编辑请求。
 *
 * version 必填：编辑页打开时把详情里的 version 回传，Service 用它做 CAS。
 * 不传 version 就等于放弃并发保护，两个编辑同时保存时后写覆盖先写，
 * 所以这里在契约层面强制要求（缺了直接 400，而不是静默丢掉保护）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleUpdateDTO extends ArticleSaveDTO {

    @NotNull(message = "缺少文章 id")
    private Long id;

    @NotNull(message = "缺少版本号，请刷新后重试")
    private Integer version;
}
