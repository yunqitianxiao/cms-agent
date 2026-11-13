package com.ylf.cms.content.service;

import com.ylf.cms.common.result.PageResult;
import com.ylf.cms.content.dto.ArticleQueryDTO;
import com.ylf.cms.content.dto.ArticleSaveDTO;
import com.ylf.cms.content.dto.ArticleUpdateDTO;
import com.ylf.cms.content.vo.ArticleVO;

/**
 * 文章服务（M2 对外契约）。
 *
 * Controller 只做参数接收与权限注解，状态机、乐观锁、越权判断全部收在这层，
 * 保证 HTTP 入口之外（以后的定时任务、数据迁移工具）调同一套规则。
 */
public interface ArticleService {

    /** 新建文章，落库状态固定为草稿，返回新文章 id */
    Long create(ArticleSaveDTO dto);

    /** 详情（含作者昵称、分类名），不存在抛 40400 */
    ArticleVO getById(Long id);

    /** 分页列表：分类筛选按"分类及其子分类"展开，无 N+1 */
    PageResult<ArticleVO> page(ArticleQueryDTO query);

    /** 编辑内容：校验状态可编辑 + 乐观锁 CAS（版本冲突 40900） */
    void update(ArticleUpdateDTO dto);

    /** 删除：仅作者可删草稿/已驳回；有阅读量的已发布文章只能下架不能删 */
    void delete(Long id);

    /** 提交审核：草稿/已驳回 -> 待审核（其他状态 40901） */
    void submit(Long id);

    /** 审核：待审核 -> 已发布 / 已驳回；驳回必须填写意见（其他状态 40901） */
    void audit(Long id, boolean pass, String opinion);

    /** 下架：已发布 -> 已下架；必须填写原因（其他状态 40901） */
    void offline(Long id, String reason);

    /**
     * 统计某作者名下"未删除且未被驳回"的稿件数，供 M1 删除账号前做守卫（>0 返回 40903）。
     * 已驳回的稿子不占着账号：作者已经不打算要它，账号可以正常注销。
     */
    long countByAuthor(Long authorId);
}
