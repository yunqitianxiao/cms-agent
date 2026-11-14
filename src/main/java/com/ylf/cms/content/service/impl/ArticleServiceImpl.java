package com.ylf.cms.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ylf.cms.common.constant.Perm;
import com.ylf.cms.common.exception.BizException;
import com.ylf.cms.common.result.PageResult;
import com.ylf.cms.common.result.ResultCode;
import com.ylf.cms.content.dto.ArticleQueryDTO;
import com.ylf.cms.content.dto.ArticleSaveDTO;
import com.ylf.cms.content.dto.ArticleUpdateDTO;
import com.ylf.cms.content.entity.CmsArticle;
import com.ylf.cms.content.enums.ArticleStatusEnum;
import com.ylf.cms.content.mapper.CmsArticleMapper;
import com.ylf.cms.content.service.ArticleService;
import com.ylf.cms.content.vo.ArticleVO;
import com.ylf.cms.security.LoginUser;
import com.ylf.cms.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 文章服务实现（M2）。
 *
 * 三条硬规则都在这一层收口，Controller 不做业务判断：
 * 1) 状态机：只有上一步合法才能进下一步，非法流转一律 40901；
 * 2) 乐观锁：所有"读-改-写"都带 version 做 CAS，冲突返回 40900，不静默覆盖；
 * 3) 越权：作者只能动自己的稿子，管理员凭 article:*:any 权限放行，否则 40301。
 *
 * 状态流转统一走 transition()，避免各方法各写一遍 update 导致某处漏了 version。
 */
@Service
public class ArticleServiceImpl implements ArticleService {

    private final CmsArticleMapper articleMapper;

    public ArticleServiceImpl(CmsArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ArticleSaveDTO dto) {
        LoginUser user = UserContext.require();
        CmsArticle article = new CmsArticle();
        article.setTitle(dto.getTitle());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCategoryId(dto.getCategoryId());
        article.setAuthorId(user.getId());
        article.setStatus(ArticleStatusEnum.DRAFT.getCode());
        article.setVersion(0);
        article.setViewCount(0);
        articleMapper.insert(article);
        return article.getId();
    }

    @Override
    public ArticleVO getById(Long id) {
        ArticleVO vo = articleMapper.selectVOById(id);
        if (vo == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        ArticleStatusEnum status = ArticleStatusEnum.of(vo.getStatus());
        vo.setStatusDesc(status == null ? vo.getStatus() : status.getDesc());

        // 未发布的稿子不对外开放：非作者本人且无管理权限时按"不存在"处理，
        // 而不是返回 40301——否则会泄露"这个 id 上有稿子"。
        if (status == null || !status.visible()) {
            LoginUser user = UserContext.get();
            if (user == null
                    || (!user.getId().equals(vo.getAuthorId()) && !user.hasPermission(Perm.ARTICLE_UPDATE_ANY))) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
        }
        return vo;
    }

    @Override
    public PageResult<ArticleVO> page(ArticleQueryDTO query) {
        Page<CmsArticle> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<CmsArticle> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTitle())) {
            wrapper.like(CmsArticle::getTitle, query.getTitle());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(CmsArticle::getStatus, query.getStatus());
        }
        if (query.getCategoryId() != null) {
            // 子分类展开在 D4 接入（届时改为 categoryIds IN 查询）
            wrapper.eq(CmsArticle::getCategoryId, query.getCategoryId());
        }
        if (query.getAuthorId() != null) {
            wrapper.eq(CmsArticle::getAuthorId, query.getAuthorId());
        }
        wrapper.orderByDesc(CmsArticle::getCreateTime);

        Page<CmsArticle> result = articleMapper.selectPage(page, wrapper);
        // 作者昵称/分类名称的联查在 D4 落地：这里先单表分页，只为把
        // 分页与总数两条 SQL 的骨架跑通，避免 D3 就引入逐行补名字的 N+1。
        return PageResult.of(result, this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ArticleUpdateDTO dto) {
        CmsArticle existing = loadExisting(dto.getId());
        requireStatus(existing, ArticleStatusEnum.DRAFT, ArticleStatusEnum.REJECTED);
        checkWritableBy(existing);

        CmsArticle update = new CmsArticle();
        update.setId(dto.getId());
        update.setVersion(dto.getVersion());
        update.setTitle(dto.getTitle());
        update.setSummary(dto.getSummary());
        update.setContent(dto.getContent());
        update.setCategoryId(dto.getCategoryId());
        int rows = articleMapper.updateById(update);
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CmsArticle existing = loadExisting(id);
        requireStatus(existing, ArticleStatusEnum.DRAFT, ArticleStatusEnum.REJECTED);
        LoginUser user = UserContext.require();
        if (!isOwner(existing, user) && !user.hasPermission(Perm.ARTICLE_DELETE_ANY)) {
            throw new BizException(ResultCode.NOT_OWNER);
        }
        // @TableLogic：这里走的是逻辑删除，历史数据仍可追溯
        articleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        CmsArticle existing = loadExisting(id);
        requireStatus(existing, ArticleStatusEnum.DRAFT, ArticleStatusEnum.REJECTED);
        LoginUser user = UserContext.require();
        if (!isOwner(existing, user) && !user.hasPermission(Perm.ARTICLE_SUBMIT)) {
            throw new BizException(ResultCode.NOT_OWNER);
        }
        CmsArticle update = new CmsArticle();
        update.setSubmitTime(LocalDateTime.now());
        transition(existing, ArticleStatusEnum.PENDING, update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean pass, String opinion) {
        LoginUser user = UserContext.require();
        if (!user.hasPermission(Perm.ARTICLE_AUDIT)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        CmsArticle existing = loadExisting(id);
        requireStatus(existing, ArticleStatusEnum.PENDING);
        if (!pass && !StringUtils.hasText(opinion)) {
            throw new BizException(ResultCode.BAD_REQUEST, "驳回必须填写审核意见");
        }

        CmsArticle update = new CmsArticle();
        update.setAuditorId(user.getId());
        update.setAuditTime(LocalDateTime.now());
        update.setAuditOpinion(opinion);
        if (pass) {
            update.setPublishTime(LocalDateTime.now());
            transition(existing, ArticleStatusEnum.PUBLISHED, update);
        } else {
            transition(existing, ArticleStatusEnum.REJECTED, update);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id, String reason) {
        LoginUser user = UserContext.require();
        if (!user.hasPermission(Perm.ARTICLE_PUBLISH)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ResultCode.BAD_REQUEST, "下架必须填写原因");
        }
        CmsArticle existing = loadExisting(id);
        requireStatus(existing, ArticleStatusEnum.PUBLISHED);

        CmsArticle update = new CmsArticle();
        update.setOfflineReason(reason);
        transition(existing, ArticleStatusEnum.OFFLINE, update);
    }

    @Override
    public long countByAuthor(Long authorId) {
        Long count = articleMapper.selectCount(new LambdaQueryWrapper<CmsArticle>()
                .eq(CmsArticle::getAuthorId, authorId)
                .ne(CmsArticle::getStatus, ArticleStatusEnum.REJECTED.getCode()));
        return count == null ? 0L : count;
    }

    /**
     * 状态流转的唯一入口：带 version 做 CAS，受影响行数为 0 说明期间被别人改过。
     * 所有流转都必须经过这里，避免某个方法忘了 setVersion 让乐观锁形同虚设。
     */
    private void transition(CmsArticle existing, ArticleStatusEnum target, CmsArticle extraFields) {
        extraFields.setId(existing.getId());
        extraFields.setVersion(existing.getVersion());
        extraFields.setStatus(target.getCode());
        int rows = articleMapper.updateById(extraFields);
        if (rows == 0) {
            throw new BizException(ResultCode.CONFLICT);
        }
    }

    private CmsArticle loadExisting(Long id) {
        CmsArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return article;
    }

    /** 校验当前状态是否在允许集合内，不在则 40901（例如草稿直接审核通过） */
    private void requireStatus(CmsArticle article, ArticleStatusEnum... allowed) {
        ArticleStatusEnum current = ArticleStatusEnum.of(article.getStatus());
        if (current == null) {
            throw new BizException(ResultCode.INVALID_STATE);
        }
        for (ArticleStatusEnum candidate : allowed) {
            if (candidate == current) {
                return;
            }
        }
        throw new BizException(ResultCode.INVALID_STATE);
    }

    /** 编辑/删除类操作的越权校验：作者本人，或持有管理级权限 */
    private void checkWritableBy(CmsArticle article) {
        LoginUser user = UserContext.require();
        if (!isOwner(article, user) && !user.hasPermission(Perm.ARTICLE_UPDATE_ANY)) {
            throw new BizException(ResultCode.NOT_OWNER);
        }
    }

    private boolean isOwner(CmsArticle article, LoginUser user) {
        return user.getId() != null && user.getId().equals(article.getAuthorId());
    }

    private ArticleVO toVO(CmsArticle article) {
        ArticleVO vo = new ArticleVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setContent(article.getContent());
        vo.setAuthorId(article.getAuthorId());
        vo.setCategoryId(article.getCategoryId());
        vo.setStatus(article.getStatus());
        ArticleStatusEnum status = ArticleStatusEnum.of(article.getStatus());
        vo.setStatusDesc(status == null ? article.getStatus() : status.getDesc());
        vo.setVersion(article.getVersion());
        vo.setViewCount(article.getViewCount());
        vo.setSubmitTime(article.getSubmitTime());
        vo.setPublishTime(article.getPublishTime());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }
}
