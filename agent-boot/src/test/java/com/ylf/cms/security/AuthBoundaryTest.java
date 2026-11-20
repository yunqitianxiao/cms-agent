package com.ylf.cms.security;

import com.ylf.cms.config.WebMvcConfig;
import com.ylf.cms.security.user.controller.AuthController;
import com.ylf.cms.security.user.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D5 的判据（集成层）：真的走 WebMvcConfig 注册的拦截器，确认保护边界是
 * "路径前缀 /api/**"，而不是"碰巧有这个 Controller"。
 *
 * 用 @WebMvcTest 而不是 @SpringBootTest：这一块不该为了验证一个拦截器去连数据库。
 * AuthService 被 mock 掉，所以下面没有任何断言依赖 MySQL 是否在跑。
 */
@WebMvcTest(controllers = AuthController.class)
@Import({WebMvcConfig.class, LoginInterceptor.class})
class AuthBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("未登录访问 /api/auth/me 返回 401，响应体带 40100")
    void meRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("不存在的 /api 路径也返回 401，说明保护按前缀生效")
    void unknownApiPathIsStillProtected() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("登录、注册、门户路径放行：空 body 到校验层返回 400 而不是 401")
    void publicPathsBypassInterceptor() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/portal/pages"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("退出登录必须先登录：未登录 POST /api/auth/logout 是 401")
    void logoutRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
