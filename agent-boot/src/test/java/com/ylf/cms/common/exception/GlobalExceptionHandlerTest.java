package com.ylf.cms.common.exception;

import com.ylf.cms.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * D2 的判据：未捕获异常只对外回"系统内部错误"，堆栈与 SQL 只进服务端日志。
 *
 * 这条测试能失败：把 handleOther 改成回 e.getMessage() 或 e.toString()，
 * 下面四个"不许出现"的断言立刻变红。
 */
class GlobalExceptionHandlerTest {

    /** 模拟一次真实的数据库故障：异常消息和 cause 里都带着攻击者想看到的东西 */
    @RestController
    static class BoomController {

        @GetMapping("/boom/sql")
        public String sql() {
            return commit(new IllegalStateException(
                    "nested exception is java.sql.SQLException: select * from sys_user where password = 'admin'",
                    new RuntimeException("at com.ylf.cms.security.user.service.UserService.login(UserService.java:88)")));
        }

        @GetMapping("/boom/biz")
        public String biz() {
            throw new BizException(ResultCode.NOT_OWNER);
        }

        private String commit(RuntimeException e) {
            throw e;
        }
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new BoomController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("未捕获异常回 500，响应体不泄露堆栈与 SQL")
    void unknownExceptionLeaksNothing() throws Exception {
        String body = mvc().perform(get("/boom/sql"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        int status = mvc().perform(get("/boom/sql")).andReturn().getResponse().getStatus();
        assertEquals(500, status);
        assertTrue(body.contains("\"code\":" + ResultCode.INTERNAL.getCode()), "应回系统内部错误码：" + body);

        for (String secret : new String[]{"select", "SQL", "java.", "IllegalState", "at com.ylf", "sys_user", "password"}) {
            assertFalse(body.contains(secret), "响应体泄露了「" + secret + "」：" + body);
        }
    }

    @Test
    @DisplayName("业务异常按声明的 HTTP 状态与业务码返回")
    void bizExceptionKeepsItsOwnCode() throws Exception {
        var response = mvc().perform(get("/boom/biz")).andReturn().getResponse();

        assertEquals(ResultCode.NOT_OWNER.getStatus().value(), response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8)
                .contains("\"code\":" + ResultCode.NOT_OWNER.getCode()));
    }
}
