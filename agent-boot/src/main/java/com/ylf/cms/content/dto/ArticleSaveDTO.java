package com.ylf.cms.content.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 文章保存（新建草稿）请求。
 *
 * 只收"作者能填"的字段：状态、审核人、发布时间全部由服务端根据动作推导，
 * 客户端传状态会被忽略（DTO 里根本没有这个字段），从源头上堵住"自己把草稿改成已发布"。
 */
@Data
public class ArticleSaveDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最多 200 字")
    private String title;

    @Size(max = 500, message = "摘要最多 500 字")
    private String summary;

    @NotBlank(message = "正文不能为空")
    private String content;

    @NotNull(message = "请选择分类")
    private Long categoryId;
}
