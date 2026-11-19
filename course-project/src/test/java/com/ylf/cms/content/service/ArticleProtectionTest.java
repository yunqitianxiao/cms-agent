package com.ylf.cms.content.service;
import com.ylf.cms.content.dto.ArticleSaveRequest;
import com.ylf.cms.content.entity.CmsArticle;
import com.ylf.cms.content.mapper.CmsArticleMapper;
import com.ylf.cms.catalog.category.service.CategoryService;
import com.ylf.cms.common.security.LoginUser;
import com.ylf.cms.common.security.UserContext;
import com.ylf.cms.common.exception.BizException;
import com.ylf.cms.common.result.ResultCode;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ArticleProtectionTest {
    CmsArticleMapper mapper; ArticleService service;
    @BeforeEach void prepare() {
        mapper=mock(CmsArticleMapper.class);
        service=new ArticleService(mapper,mock(CategoryService.class),new ArticleSafetyService(),mock(PlatformTransactionManager.class));
        LoginUser user=new LoginUser();user.setId(1L);user.setPermissions(Set.of("article:update:own","article:submit"));
        UserContext.set(user);
    }
    @AfterEach void release() {UserContext.clear();}
    ArticleSaveRequest request(String text) {
        ArticleSaveRequest request=new ArticleSaveRequest();request.setTitle("测试稿件");request.setContent(text);request.setCategoryId(1L);request.setVersion(0);return request;
    }
    CmsArticle article() {
        CmsArticle article=new CmsArticle();article.setId(1L);article.setAuthorId(1L);article.setStatus("DRAFT");article.setVersion(0);article.setCategoryId(1L);return article;
    }
    @Test void createRejectsSensitiveContentWithoutDatabaseWrite() {
        BizException ex=assertThrows(BizException.class,()->service.create(request("邮箱 reader@example.com")));
        assertEquals(ResultCode.SENSITIVE_INFO,ex.getResultCode());verify(mapper,never()).insert(any(CmsArticle.class));
    }
    @Test void updateRejectsSensitiveContentWithoutDatabaseWrite() {
        when(mapper.selectById(1L)).thenReturn(article());
        BizException ex=assertThrows(BizException.class,()->service.update(1L,request("密码：demo-pass")));
        assertEquals(ResultCode.SENSITIVE_INFO,ex.getResultCode());verify(mapper,never()).updateById(any());
    }
    @Test void precheckReturnsFieldAndPositionOnly() {
        var hits=service.checkSensitive("联系 reader@example.com",null,"普通正文");
        assertEquals("title",hits.get(0).getField());assertEquals(3,hits.get(0).getStart());
        assertFalse(hits.toString().contains("reader@example.com"));
    }
    @Test void stateTransitionReportsOptimisticLockConflict() {
        when(mapper.selectById(1L)).thenReturn(article());when(mapper.updateById(any())).thenReturn(0);
        BizException ex=assertThrows(BizException.class,()->service.submit(1L));
        assertEquals(ResultCode.CONFLICT,ex.getResultCode());
    }
    @Test void refusesEditingAnotherAuthorsDraft() {
        CmsArticle article=article();article.setAuthorId(2L);when(mapper.selectById(1L)).thenReturn(article);
        assertEquals(ResultCode.NOT_OWNER,assertThrows(BizException.class,()->service.update(1L,request("普通正文"))).getResultCode());
    }
}
