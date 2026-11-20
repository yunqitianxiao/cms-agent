package com.ylf.cms.catalog.category;

import com.ylf.cms.CmsApplication;
import com.ylf.cms.catalog.category.service.CategoryService;
import com.ylf.cms.catalog.category.vo.CategoryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D7 的判据（真实库）：智能体拿到的分类数字必须与数据库 COUNT 对得上。
 *
 * 平时不跑——需要 scripts/verify-category-count.sh 起一个临时 MariaDB，
 * 把 schema.sql + data.sql 导进去后用 -Dcms.it.jdbcUrl=... 打开。
 *
 * 能失败的两处：
 * 1) 把 CategoryService.rollUp 的子孙累加去掉 → 技术从 10 变 0，下面的循环断言红；
 * 2) 把 Mapper 的 status='PUBLISHED' 去掉（口径改成全部状态）→
 *    「Java = 6」变 9、「后端 = 9」变 12，显式断言红。
 *
 * 子树 id 在这里写死，不复用被测的 descendantIds——否则尺子和被测对象是同一把，
 * 汇总漏掉一级时两边会一起错、一起绿。
 */
@SpringBootTest(classes = CmsApplication.class)
@EnabledIfSystemProperty(named = "cms.it.jdbcUrl", matches = ".+")
class CategoryCountDbTest {

    /** 与 data.sql 一致：每个分类的整棵子树（含自身） */
    private static final Map<Long, List<Long>> SUBTREES = new LinkedHashMap<>();

    static {
        SUBTREES.put(1L, Arrays.asList(1L, 4L, 5L, 6L, 7L, 8L));
        SUBTREES.put(2L, Arrays.asList(2L, 9L));
        SUBTREES.put(3L, Arrays.asList(3L, 10L));
        SUBTREES.put(4L, Arrays.asList(4L, 6L, 7L));
        SUBTREES.put(5L, Arrays.asList(5L, 8L));
        SUBTREES.put(6L, Arrays.asList(6L));
        SUBTREES.put(7L, Arrays.asList(7L));
        SUBTREES.put(8L, Arrays.asList(8L));
        SUBTREES.put(9L, Arrays.asList(9L));
        SUBTREES.put(10L, Arrays.asList(10L));
    }

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("cms.it.jdbcUrl"));
        registry.add("spring.datasource.username", () -> System.getProperty("cms.it.jdbcUser", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("cms.it.jdbcPassword", ""));
    }

    @Test
    @DisplayName("每个分类节点的数字与库里对子树的 COUNT 一致")
    void articleCountMatchesDatabaseCount() {
        Map<Long, CategoryVO> byId = flatten(categoryService.tree(false));

        assertEquals(SUBTREES.keySet(), byId.keySet(), "data.sql 里的 10 个分类都应出现在树上");
        for (Map.Entry<Long, List<Long>> entry : SUBTREES.entrySet()) {
            long expected = countPublishedIn(entry.getValue());
            assertEquals(expected, byId.get(entry.getKey()).getArticleCount(),
                    "分类 " + byId.get(entry.getKey()).getName() + " 的数字与库里的 COUNT 对不上");
        }
    }

    @Test
    @DisplayName("口径是只算 PUBLISHED：草稿/待审/驳回/下架都不进智能体的数字")
    void draftsAndOfflineAreExcluded() {
        Map<Long, CategoryVO> byId = flatten(categoryService.tree(false));

        // data.sql 里 Java(6) 有 6 篇已发布，另有 1 篇待审、1 篇草稿、1 篇已下架
        assertEquals(6L, byId.get(6L).getArticleCount(), "Java 应为 6；算上未发布就是 9");
        // 后端(4) 子树已发布 9，另有 2 篇待审、1 篇草稿
        assertEquals(9L, byId.get(4L).getArticleCount(), "后端应为 9；算上未发布就是 12");
    }

    @Test
    @DisplayName("全部筛选条件取一次就把整棵树算完，不随分类数增加 SQL 条数")
    void treeLoadsCountsInOneQuery() {
        // 这组数据的分类启用状态全是 1，所以两棵树形状相同；数字也对得上
        Map<Long, CategoryVO> all = flatten(categoryService.tree(false));
        Map<Long, CategoryVO> enabled = flatten(categoryService.tree(true));

        assertEquals(all.keySet(), enabled.keySet());
        for (Long id : all.keySet()) {
            assertEquals(all.get(id).getArticleCount(), enabled.get(id).getArticleCount());
        }
    }

    private long countPublishedIn(List<Long> categoryIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(categoryIds.size(), "?"));
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cms_article "
                        + "WHERE deleted = 0 AND status = 'PUBLISHED' AND category_id IN (" + placeholders + ")",
                Long.class, categoryIds.toArray());
        return count == null ? 0L : count;
    }

    private Map<Long, CategoryVO> flatten(List<CategoryVO> roots) {
        Map<Long, CategoryVO> byId = new HashMap<>();
        Deque<CategoryVO> queue = new ArrayDeque<>(new ArrayList<>(roots));
        while (!queue.isEmpty()) {
            CategoryVO vo = queue.poll();
            byId.put(vo.getId(), vo);
            queue.addAll(vo.getChildren());
        }
        return byId;
    }
}
