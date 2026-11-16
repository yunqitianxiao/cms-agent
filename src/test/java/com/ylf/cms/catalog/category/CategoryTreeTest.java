package com.ylf.cms.catalog.category;

import com.ylf.cms.catalog.category.entity.CmsCategory;
import com.ylf.cms.catalog.category.mapper.CategoryCountRow;
import com.ylf.cms.catalog.category.mapper.CmsCategoryMapper;
import com.ylf.cms.catalog.category.service.CategoryService;
import com.ylf.cms.catalog.category.vo.CategoryVO;
import com.ylf.cms.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D7 的判据（逻辑层）：树的形状与"父节点数字 = 自身 + 全部子孙"的汇总。
 *
 * 能失败：把 CategoryService.rollUp 里 `total += rollUp(child)` 删掉，
 * 「技术 = 10」立刻变成「技术 = 0」。
 *
 * 真实库那一条在 CategoryCountDbTest（由 scripts/verify-category-count.sh 开）。
 */
class CategoryTreeTest {

    private static final long ROOT = 0L;

    private CmsCategoryMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = mock(CmsCategoryMapper.class);
        when(mapper.selectAllAlive()).thenReturn(categories());
        when(mapper.countPublishedByCategory()).thenReturn(publishedCounts());
    }

    /**
     * 与 data.sql 同一棵分类树，但文章数换成便于口算的分布：
     * Java=6、数据库=3、CSS=1、需求分析=1、活动策划=1 ⇒ 后端=9、前端=1、技术=10。
     */
    private List<CmsCategory> categories() {
        List<CmsCategory> list = new ArrayList<>();
        list.add(category(1, ROOT, "技术", 1, 10, 1));
        list.add(category(2, ROOT, "产品", 1, 20, 1));
        list.add(category(3, ROOT, "运营", 1, 30, 1));
        list.add(category(4, 1, "后端", 2, 10, 1));
        list.add(category(5, 1, "前端", 2, 20, 1));
        list.add(category(6, 4, "Java", 3, 10, 1));
        list.add(category(7, 4, "数据库", 3, 20, 1));
        list.add(category(8, 5, "CSS", 3, 10, 1));
        list.add(category(9, 2, "需求分析", 2, 10, 1));
        list.add(category(10, 3, "活动策划", 2, 10, 1));
        return list;
    }

    private List<CategoryCountRow> publishedCounts() {
        List<CategoryCountRow> list = new ArrayList<>();
        list.add(countRow(6, 6));
        list.add(countRow(7, 3));
        list.add(countRow(8, 1));
        list.add(countRow(9, 1));
        list.add(countRow(10, 1));
        return list;
    }

    @Test
    @DisplayName("树按 sort 排，子节点挂在正确的父节点下")
    void treeShape() {
        List<CategoryVO> roots = service().tree(false);

        assertEquals(List.of(1L, 2L, 3L), roots.stream().map(CategoryVO::getId).collect(Collectors.toList()));
        CategoryVO tech = roots.get(0);
        assertEquals(List.of(4L, 5L), tech.getChildren().stream().map(CategoryVO::getId).collect(Collectors.toList()));
        assertEquals(List.of(6L, 7L),
                tech.getChildren().get(0).getChildren().stream().map(CategoryVO::getId).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("父节点数字 = 自身 + 全部子孙，不是只算直接子级")
    void articleCountRollsUpThroughAllLevels() {
        Map<Long, CategoryVO> byId = flatten(service().tree(false));

        assertEquals(6L, byId.get(6L).getArticleCount());
        assertEquals(3L, byId.get(7L).getArticleCount());
        assertEquals(9L, byId.get(4L).getArticleCount(), "后端 = 自身 0 + Java 6 + 数据库 3");
        assertEquals(1L, byId.get(5L).getArticleCount());
        assertEquals(10L, byId.get(1L).getArticleCount(), "技术 = 后端 9 + 前端 1");
        assertEquals(1L, byId.get(2L).getArticleCount());
        assertEquals(1L, byId.get(3L).getArticleCount());
    }

    @Test
    @DisplayName("没有文章的叶子显示 0，而不是 null")
    void countIsZeroForEmptyLeaf() {
        when(mapper.countPublishedByCategory()).thenReturn(new ArrayList<>());

        Map<Long, CategoryVO> byId = flatten(service().tree(false));

        assertEquals(0L, byId.get(6L).getArticleCount());
        assertEquals(0L, byId.get(1L).getArticleCount());
    }

    @Test
    @DisplayName("onlyEnabled=true 时停用分类连同整棵子树消失，但父节点数字仍包含它")
    void disabledSubtreeIsPrunedButCountKept() {
        List<CmsCategory> list = categories();
        list.stream().filter(c -> c.getId() == 5L).forEach(c -> c.setStatus(0));
        when(mapper.selectAllAlive()).thenReturn(list);

        List<CategoryVO> roots = service().tree(true);

        Map<Long, CategoryVO> byId = flatten(roots);
        assertTrue(!byId.containsKey(5L), "停用的前端不该出现");
        assertTrue(!byId.containsKey(8L), "前端被剪掉时 CSS 也该一起消失，不能挂到根上伪装成一级分类");
        assertEquals(10L, byId.get(1L).getArticleCount(),
                "剪枝只影响展示，不改变历史文章归属：技术仍含已隐藏的 CSS 那 1 篇");
    }

    @Test
    @DisplayName("子树 id 含自身，能展开到第三级")
    void descendantIdsCoversWholeSubtree() {
        assertEquals(Set.of(1L, 4L, 5L, 6L, 7L, 8L), new HashSet<>(service().descendantIds(1L)));
        assertEquals(Set.of(6L), new HashSet<>(service().descendantIds(6L)));
        assertEquals(Set.of(3L, 10L), new HashSet<>(service().descendantIds(3L)));
    }

    @Test
    @DisplayName("分类不存在时返回空列表，而不是抛异常或返回全表")
    void descendantIdsOfUnknownIsEmpty() {
        assertTrue(service().descendantIds(999L).isEmpty());
        assertTrue(service().descendantIds(null).isEmpty());
    }

    @Test
    @DisplayName("parent_id 指向自己时不死循环")
    void selfParentDoesNotLoopForever() {
        List<CmsCategory> list = categories();
        list.add(category(99, 99, "自环", 1, 90, 1));
        when(mapper.selectAllAlive()).thenReturn(list);

        assertEquals(Set.of(99L), new HashSet<>(service().descendantIds(99L)));
    }

    @Test
    @DisplayName("父分类被逻辑删除后，子分类挂到根上而不是凭空消失")
    void orphanSubtreeBecomesRoot() {
        when(mapper.selectAllAlive()).thenReturn(categories().stream()
                .filter(c -> c.getId() != 4L) // 后端被逻辑删除
                .collect(Collectors.toList()));

        List<CategoryVO> roots = service().tree(false);
        Map<Long, CategoryVO> byId = flatten(roots);

        assertNotNull(byId.get(6L), "Java 不能跟着父分类一起消失");
        assertTrue(roots.stream().anyMatch(vo -> vo.getId() == 6L), "失去父节点的 Java 应直接挂到根");
    }

    @Test
    @DisplayName("发布时分类停用或不存在都要拒，且错误码是参数类而非权限类")
    void requireEnabledRejectsDisabledAndMissing() {
        when(mapper.selectById(5L)).thenReturn(category(5, 1, "前端", 2, 20, 0));
        when(mapper.selectById(7L)).thenReturn(category(7, 4, "数据库", 3, 20, 1));
        CategoryService service = service();

        BizException disabled = assertThrows(BizException.class, () -> service.requireEnabled(5L));
        assertEquals(40000, disabled.getResultCode().getCode());

        BizException missing = assertThrows(BizException.class, () -> service.requireEnabled(999L));
        assertEquals(40000, missing.getResultCode().getCode());

        assertEquals("数据库", service.requireEnabled(7L).getName());
    }

    @Test
    @DisplayName("扁平列表按层级、排序号排，供智能体判断站点有没有配分类")
    void listFlatIsOrderedByLevel() {
        List<CategoryVO> flat = service().listFlat();

        assertEquals(List.of(1L, 2L, 3L, 4L, 9L, 10L, 5L, 6L, 8L, 7L),
                flat.stream().map(CategoryVO::getId).collect(Collectors.toList()));
    }

    private CategoryService service() {
        return new CategoryService(mapper);
    }

    private static Map<Long, CategoryVO> flatten(Collection<CategoryVO> roots) {
        Map<Long, CategoryVO> byId = new java.util.HashMap<>();
        Deque<CategoryVO> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            CategoryVO vo = queue.poll();
            byId.put(vo.getId(), vo);
            queue.addAll(vo.getChildren());
        }
        return byId;
    }

    private static CmsCategory category(long id, long parentId, String name, int level, int sort, int status) {
        CmsCategory category = new CmsCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        category.setLevel(level);
        category.setSort(sort);
        category.setStatus(status);
        return category;
    }

    private static CategoryCountRow countRow(long categoryId, long total) {
        CategoryCountRow row = new CategoryCountRow();
        row.setCategoryId(categoryId);
        row.setTotal(total);
        return row;
    }
}
