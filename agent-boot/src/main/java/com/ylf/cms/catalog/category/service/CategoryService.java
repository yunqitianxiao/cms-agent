package com.ylf.cms.catalog.category.service;

import com.ylf.cms.catalog.category.entity.CmsCategory;
import com.ylf.cms.catalog.category.mapper.CategoryCountRow;
import com.ylf.cms.catalog.category.mapper.CmsCategoryMapper;
import com.ylf.cms.catalog.category.vo.CategoryVO;
import com.ylf.cms.common.exception.BizException;
import com.ylf.cms.common.result.ResultCode;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分类的只读服务。
 *
 * 智能体要回答"某个分类下有多少篇文章"、以及"站点里有没有配分类"，
 * 这两件事都必须来自数据库而不是手写常量，所以这里同时提供树与计数。
 *
 * 计数口径（写死在这里，测试按同一口径断言）：
 * 节点数字 = 自身 + 全部子孙下"已发布且未删除"的文章数；
 * 与分类自身是否启用无关——停用只是前端不给选，不改变历史文章的归属。
 */
@Service
public class CategoryService {

    /** 分类最多三级，与 schema.sql 的 level 注释一致 */
    public static final int MAX_LEVEL = 3;

    private static final long ROOT_PARENT_ID = 0L;

    private final CmsCategoryMapper categoryMapper;

    public CategoryService(CmsCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /**
     * 分类树，按 sort、id 排序。
     *
     * @param onlyEnabled true 时停用分类连同其整棵子树一并移除。这里刻意不把
     *                    "父停用、子启用"的子节点挂到根上——那样它看起来像一个
     *                    一级分类，用户会以为站点结构变了。计数在剪枝之前算好，
     *                    所以父节点的数字仍包含被剪掉的子树。
     */
    public List<CategoryVO> tree(boolean onlyEnabled) {
        List<CmsCategory> all = categoryMapper.selectAllAlive();
        Map<Long, Long> directCounts = loadDirectCounts();

        Map<Long, CategoryVO> byId = new LinkedHashMap<>();
        for (CmsCategory category : all) {
            CategoryVO vo = toVO(category);
            vo.setArticleCount(directCounts.getOrDefault(category.getId(), 0L));
            byId.put(category.getId(), vo);
        }

        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO vo : byId.values()) {
            CategoryVO parent = (vo.getParentId() == null || vo.getParentId() == ROOT_PARENT_ID)
                    ? null : byId.get(vo.getParentId());
            if (parent == null) {
                // 根节点，或父分类已被逻辑删除——后者不能悄悄消失，挂到根上并保持
                // 它自己的文章计数，否则"技术 > 后端 > Java"里的后端一删，Java 就没了
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }

        for (CategoryVO root : roots) {
            rollUp(root);
        }
        return onlyEnabled ? pruneDisabled(roots) : roots;
    }

    /** 扁平列表，按层级、排序号排。智能体用它判断"站点配了分类没有"。 */
    public List<CategoryVO> listFlat() {
        List<CmsCategory> all = categoryMapper.selectAllAlive();
        List<CategoryVO> list = new ArrayList<>(all.size());
        for (CmsCategory category : all) {
            list.add(toVO(category));
        }
        list.sort(Comparator.comparing(CategoryVO::getLevel, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(CategoryVO::getSort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(CategoryVO::getId, Comparator.nullsLast(Long::compareTo)));
        return list;
    }

    /**
     * 某分类及其全部子孙的 id（含自身）。文章列表"按分类筛选"选父分类时，
     * 要能看到子分类下的文章，否则用户会觉得列表漏数据。
     *
     * 分类不存在（或已逻辑删除）时返回空列表，不抛异常——调用方在拼 IN 条件，
     * 空列表天然得到"没有结果"。
     */
    public List<Long> descendantIds(Long rootId) {
        if (rootId == null) {
            return Collections.emptyList();
        }
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        Set<Long> known = new HashSet<>();
        for (CmsCategory category : categoryMapper.selectAllAlive()) {
            known.add(category.getId());
            long parentId = category.getParentId() == null ? ROOT_PARENT_ID : category.getParentId();
            childrenOf.computeIfAbsent(parentId, key -> new ArrayList<>()).add(category.getId());
        }
        if (!known.contains(rootId)) {
            return Collections.emptyList();
        }

        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long id = stack.pop();
            // parent_id 指向自己或形成环时，visited 保证不重复展开、不死循环
            if (!visited.add(id)) {
                continue;
            }
            result.add(id);
            List<Long> children = childrenOf.get(id);
            if (children != null) {
                for (Long child : children) {
                    stack.push(child);
                }
            }
        }
        return result;
    }

    /** 发布/编辑文章时校验分类可用：必须存在且处于启用状态 */
    public CmsCategory requireEnabled(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "分类不能为空");
        }
        CmsCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "分类不存在");
        }
        if (category.getStatus() == null || category.getStatus() != 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "该分类已停用，不能用于发布");
        }
        return category;
    }

    private Map<Long, Long> loadDirectCounts() {
        Map<Long, Long> direct = new HashMap<>();
        for (CategoryCountRow row : categoryMapper.countPublishedByCategory()) {
            if (row.getCategoryId() != null && row.getTotal() != null) {
                direct.put(row.getCategoryId(), row.getTotal());
            }
        }
        return direct;
    }

    /** 自底向上把子孙的文章数加到父节点，返回该子树总数 */
    private long rollUp(CategoryVO node) {
        long total = node.getArticleCount() == null ? 0L : node.getArticleCount();
        for (CategoryVO child : node.getChildren()) {
            total += rollUp(child);
        }
        node.setArticleCount(total);
        return total;
    }

    private List<CategoryVO> pruneDisabled(List<CategoryVO> nodes) {
        List<CategoryVO> kept = new ArrayList<>();
        for (CategoryVO node : nodes) {
            if (node.getStatus() == null || node.getStatus() != 1) {
                continue;
            }
            node.setChildren(pruneDisabled(node.getChildren()));
            kept.add(node);
        }
        return kept;
    }

    private CategoryVO toVO(CmsCategory category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setParentId(category.getParentId());
        vo.setName(category.getName());
        vo.setLevel(category.getLevel());
        vo.setSort(category.getSort());
        vo.setStatus(category.getStatus());
        vo.setDescription(category.getDescription());
        return vo;
    }
}
