package com.ylf.cms.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * D4 的判据：create_time / update_time 由框架填，业务代码不手写。
 *
 * 这条测试能失败：把 AuditMetaObjectHandler.insertFill 里的 strictInsertFill
 * 两行删掉，下面两个 assertNotNull 立刻变红。
 *
 * 建表判据不在这里跑（需要数据库），用 scripts/verify-db.sh —— 它起一个临时
 * MariaDB 实例，不碰本机已有的 cms_db。
 */
class DataLayerTest {

    @TableName("cms_article")
    static class DemoArticle {

        @TableId(type = IdType.AUTO)
        private Long id;

        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createTime;

        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updateTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }
    }

    static {
        // strictInsertFill 只认 TableInfo 里标了 fill 的字段，测试实体需要先注册
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(DemoArticle.class.getName());
        TableInfoHelper.initTableInfo(assistant, DemoArticle.class);
    }

    private final AuditMetaObjectHandler handler = new AuditMetaObjectHandler();

    @Test
    @DisplayName("插入时 createTime 与 updateTime 由 handler 填成同一个时刻")
    void insertFillsBothTimestamps() {
        DemoArticle article = new DemoArticle();
        MetaObject metaObject = SystemMetaObject.forObject(article);

        handler.insertFill(metaObject);

        assertNotNull(article.getCreateTime(), "createTime 没有自动填充");
        assertNotNull(article.getUpdateTime(), "updateTime 没有自动填充");
        assertEquals(article.getCreateTime(), article.getUpdateTime());
    }

    @Test
    @DisplayName("更新时只刷新 updateTime，createTime 原样保留")
    void updateRefreshesOnlyUpdateTime() {
        DemoArticle article = new DemoArticle();
        LocalDateTime created = LocalDateTime.of(2026, 10, 1, 9, 0);
        article.setCreateTime(created);

        handler.updateFill(SystemMetaObject.forObject(article));

        assertEquals(created, article.getCreateTime(), "createTime 不该被更新覆盖");
        assertNotNull(article.getUpdateTime());
    }

    @Test
    @DisplayName("拦截器顺序：分页 → 乐观锁 → 防全表更新，分页在插件层也卡 200")
    void interceptorsAreOrdered() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();
        List<InnerInterceptor> list = interceptor.getInterceptors();

        assertEquals(3, list.size());
        assertInstanceOf(PaginationInnerInterceptor.class, list.get(0));
        assertInstanceOf(OptimisticLockerInnerInterceptor.class, list.get(1));
        assertInstanceOf(BlockAttackInnerInterceptor.class, list.get(2));
        assertEquals(200L, ((PaginationInnerInterceptor) list.get(0)).getMaxLimit(),
                "插件层上限必须与 PageQuery 的 @Max 一致，否则绕过 DTO 的内部调用仍会拖全表");
    }

    @Test
    @DisplayName("未标 fill 的字段不会被审计填充误伤")
    void nonFillFieldIsUntouched() {
        DemoArticle article = new DemoArticle();
        article.setId(42L);

        handler.insertFill(SystemMetaObject.forObject(article));

        assertEquals(42L, article.getId(), "id 不属于审计字段，不该被 handler 改写");
    }
}
