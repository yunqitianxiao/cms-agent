package com.ylf.cms.common.result;

import com.ylf.cms.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * D3 的判据：pageSize=99999 必须 400，不能默默按一万条去查库。
 *
 * 这条测试能失败：把 PageQuery 的 @Max(200) 去掉，oversizedPageSizeIsRejected
 * 立刻从 400 变成 200；把 @Min(1) 去掉，pageNum=0 那条同样变红。
 */
class PageQueryValidationTest {

    @RestController
    static class PageController {

        @GetMapping("/api/articles")
        public String list(@Valid PageQuery query) {
            return query.getPageNum() + "/" + query.getPageSize();
        }
    }

    private MockMvc mvc() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new PageController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("pageSize 超过上限回 400")
    void oversizedPageSizeIsRejected() throws Exception {
        var response = mvc().perform(get("/api/articles").param("pageSize", "99999"))
                .andReturn().getResponse();

        assertEquals(400, response.getStatus());
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"code\":" + ResultCode.BAD_REQUEST.getCode()), body);
        assertTrue(body.contains("每页最多 200 条"), body);
    }

    @Test
    @DisplayName("pageNum 从 1 开始，0 被拒")
    void pageNumStartsAtOne() throws Exception {
        var response = mvc().perform(get("/api/articles").param("pageNum", "0"))
                .andReturn().getResponse();

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("页码从 1 开始"));
    }

    @Test
    @DisplayName("不传分页参数时用默认值 1/10，合法参数原样通过")
    void legalPagingPasses() throws Exception {
        var defaulted = mvc().perform(get("/api/articles")).andReturn().getResponse();
        assertEquals(200, defaulted.getStatus());
        assertEquals("1/10", defaulted.getContentAsString(StandardCharsets.UTF_8));

        var explicit = mvc().perform(get("/api/articles")
                        .param("pageNum", "2").param("pageSize", "20"))
                .andReturn().getResponse();
        assertEquals(200, explicit.getStatus());
        assertEquals("2/20", explicit.getContentAsString(StandardCharsets.UTF_8));
    }
}
