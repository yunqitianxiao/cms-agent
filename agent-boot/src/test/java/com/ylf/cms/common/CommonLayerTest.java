package com.ylf.cms.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylf.cms.common.annotation.GuideCard;
import com.ylf.cms.common.exception.BizException;
import com.ylf.cms.common.result.PageResult;
import com.ylf.cms.common.result.ResultCode;
import com.ylf.cms.common.util.IpUtil;
import com.ylf.cms.common.util.MaskUtil;
import com.ylf.cms.common.util.SortWhitelist;
import com.ylf.cms.config.TraceIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class CommonLayerTest {

    // ---------- 排序白名单：ORDER BY 不能参数化，只能白名单放行 ----------

    private static final Map<String, String> SORTABLE = new LinkedHashMap<>();

    static {
        SORTABLE.put("createTime", "create_time");
        SORTABLE.put("title", "title");
    }

    @Test
    @DisplayName("白名单命中才拼进 ORDER BY，并追加唯一键防翻页重复")
    void sortWhitelistBuildsColumns() {
        assertEquals("create_time desc, id asc",
                SortWhitelist.build("createTime,desc", SORTABLE, "id desc", "id asc"));
    }

    @Test
    @DisplayName("白名单外字段直接拒绝，不拼进 SQL")
    void sortWhitelistRejectsUnknownField() {
        BizException e = assertThrows(BizException.class,
                () -> SortWhitelist.build("; DROP TABLE sys_user", SORTABLE, "id desc"));

        assertEquals(ResultCode.BAD_REQUEST, e.getResultCode());
        assertTrue(e.getMessage().contains("不支持的排序字段"));
    }

    @Test
    @DisplayName("排序方向只认 asc/desc，拼超过 3 个字段也拒绝")
    void sortWhitelistRejectsBadDirectionAndTooManyParts() {
        assertEquals(ResultCode.BAD_REQUEST,
                assertThrows(BizException.class,
                        () -> SortWhitelist.build("title,sideways", SORTABLE, "id desc")).getResultCode());
        assertEquals(ResultCode.BAD_REQUEST,
                assertThrows(BizException.class,
                        () -> SortWhitelist.build("title,asc;createTime,asc;title,desc;createTime,desc",
                                SORTABLE, "id desc")).getResultCode());
    }

    // ---------- 脱敏：日志里不能出现口令原文 ----------

    static class LoginReq {
        public String username = "admin";
        public String password = "s3cret-value";
        public String nickname = "old-macdonald";
    }

    @Test
    @DisplayName("敏感字段按名字替换成 ***，超长参数截断")
    void maskHidesSensitiveFields() {
        String masked = MaskUtil.mask(new Object[]{new LoginReq()}, new ObjectMapper());

        assertTrue(masked.contains("\"password\":\"***\""), masked);
        assertFalse(masked.contains("s3cret-value"), masked);
        assertTrue(masked.contains("old-macdonald"), masked);
    }

    @Test
    @DisplayName("Servlet 对象不参与序列化，避免把请求体整个打进日志")
    void maskSkipsInfrastructureBeans() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String masked = MaskUtil.mask(new Object[]{request, "ok"}, new ObjectMapper());
        assertEquals("\"ok\"", masked);
    }

    // ---------- 真实 IP：只作审计参考 ----------

    @Test
    @DisplayName("X-Forwarded-For 取第一段，为空时回落到 remoteAddr")
    void ipUtilReadsFirstForwardedHop() {
        MockHttpServletRequest withHeader = new MockHttpServletRequest();
        withHeader.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        assertEquals("203.0.113.7", IpUtil.getIp(withHeader));

        MockHttpServletRequest withoutHeader = new MockHttpServletRequest();
        withoutHeader.setRemoteAddr("198.51.100.9");
        assertEquals("198.51.100.9", IpUtil.getIp(withoutHeader));
    }

    // ---------- traceId：用户报障时凭它定位那一次请求 ----------

    @RestController
    static class EchoController {

        @GetMapping("/echo")
        public String echo() {
            return "ok";
        }
    }

    @Test
    @DisplayName("每个请求响应头都带 16 位 traceId，两次请求不重复")
    void traceIdFilterStampsEveryResponse() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EchoController())
                .addFilters(new TraceIdFilter())
                .build();

        String first = mvc.perform(get("/echo")).andReturn().getResponse().getHeader("X-Trace-Id");
        String second = mvc.perform(get("/echo")).andReturn().getResponse().getHeader("X-Trace-Id");

        assertNotNull(first);
        assertTrue(first.matches("[0-9a-f]{16}"), first);
        assertNotEquals(first, second);
    }

    // ---------- @GuideCard：智能体站点地图的结构来源 ----------

    @GuideCard(id = "article.create", title = "新建文章", group = "内容",
            route = "/articles/new", order = 10, aliases = {"发文", "写稿"})
    static class AnnotatedPage {
    }

    @Test
    @DisplayName("@GuideCard 运行时可读，否则扫描器拿不到卡片")
    void guideCardIsVisibleAtRuntime() {
        GuideCard card = AnnotatedPage.class.getAnnotation(GuideCard.class);

        assertNotNull(card, "注解保留策略必须是 RUNTIME");
        assertEquals("article.create", card.id());
        assertEquals("新建文章", card.title());
        assertEquals("/articles/new", card.route());
        assertEquals(10, card.order());
        assertTrue(Arrays.asList(card.aliases()).contains("写稿"));
    }

    // ---------- PageResult：不把 MP 的 Page 直接透给前端 ----------

    @Test
    @DisplayName("PageResult 丢掉 MP 内部字段，records 为 null 时给空列表")
    void pageResultDropsInternalFields() {
        Page<String> empty = new Page<>(1, 10);
        empty.setTotal(0);
        empty.setRecords(null);
        PageResult<String> result = PageResult.of(empty);

        assertEquals(0, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
        assertNotNull(result.getRecords());
        assertTrue(result.getRecords().isEmpty());
    }
}
