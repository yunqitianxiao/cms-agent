package com.ylf.cms.catalog.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ylf.cms.catalog.category.entity.CmsCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分类 Mapper。只放读侧的两条自写 SQL，写侧（新增/改/删）留给分类管理模块。
 *
 * 用 @Mapper 而不是启动类上的 @MapperScan：根包扫描会把 Service 接口
 * 也按 Mapper 注册，启动期报 "Invalid bound statement"（M2 已踩过）。
 */
@Mapper
public interface CmsCategoryMapper extends BaseMapper<CmsCategory> {

    /** 全部未删除分类，排序与树的展示顺序一致 */
    @Select("SELECT id, parent_id, name, level, sort, status, description "
            + "FROM cms_category WHERE deleted = 0 ORDER BY sort ASC, id ASC")
    List<CmsCategory> selectAllAlive();

    /**
     * 每个分类下"已发布且未删除"的文章数，一次分组查完。
     * 不做逐分类 count，否则分类越多 SQL 越多（N+1）。
     */
    @Select("SELECT category_id AS categoryId, COUNT(*) AS total "
            + "FROM cms_article WHERE deleted = 0 AND status = 'PUBLISHED' "
            + "GROUP BY category_id")
    List<CategoryCountRow> countPublishedByCategory();
}
