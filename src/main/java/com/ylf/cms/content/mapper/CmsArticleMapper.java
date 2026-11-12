package com.ylf.cms.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ylf.cms.content.entity.CmsArticle;
import com.ylf.cms.content.vo.ArticleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文章 Mapper（M2 内部，禁止被其他模块 import）。
 *
 * 用 @Mapper 而不是启动类上的 @MapperScan 根包扫描：
 * 根包扫描会把 common/security 下的 Service 接口也按 Mapper 注册，
 * 启动期报 "Invalid bound statement"，这是本工程踩过的坑。
 */
@Mapper
public interface CmsArticleMapper extends BaseMapper<CmsArticle> {

    /**
     * 单条详情联查（一次带出作者昵称、分类名称）。
     *
     * 详情页同样不做"先查文章、再查作者、再查分类"的三段式查询，
     * 直接一条 LEFT JOIN，既省往返次数也不会随关联表增多退化成 N+1。
     */
    ArticleVO selectVOById(@Param("id") Long id);
}
