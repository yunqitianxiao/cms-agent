package com.ylf.cms.catalog.category.mapper;

import lombok.Data;

/**
 * 一条"某分类下已发布文章数"的分组行。
 * 只是 Mapper 的返回形状，不是对外契约，所以不放在 vo 包里。
 */
@Data
public class CategoryCountRow {

    private Long categoryId;

    private Long total;
}
