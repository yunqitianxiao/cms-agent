package com.ylf.cms.catalog.category.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryVO {

    private Long id;
    private Long parentId;
    private String name;
    private Integer level;
    private Integer sort;
    private Integer status;
    private String description;

    /**
     * 该分类及其全部子孙下"已发布且未删除"的文章数。
     * 只有 {@link com.ylf.cms.catalog.category.service.CategoryService#tree(boolean)} 会填；
     * 与分类自身是否启用无关——停用只是前端不给选，不改变历史文章的归属。
     */
    private Long articleCount;

    private List<CategoryVO> children = new ArrayList<>();
}
