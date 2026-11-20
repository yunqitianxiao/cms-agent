package com.ylf.cms.security;

import com.ylf.cms.common.constant.Perm;
import com.ylf.cms.common.constant.SessionKeys;
import com.ylf.cms.security.user.controller.RoleController;
import com.ylf.cms.security.user.service.AuthService;
import com.ylf.cms.security.user.service.RoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D6 的判据（集成层）：注解真的挂在 Controller 上，切面真的被 Spring 装配起来。
 *
 * 只用单元测试证明不了这件事——@RequirePermission 可能被某个方法漏加，
 * 或者 PermissionAspect 根本没进容器（切面类不加 @Component 时 Spring 不报错，
 * 只会静默地什么都不做）。
 *
 * 这里不直接往 UserContext 里塞人：身份来自 D5 的 LoginInterceptor，
 * 由它按 Session 里的 userId 向 AuthService 重新装载——只有这样才能证明
 * "拦截器 → 上下文 → 切面" 这条链子是通的。
 */
@WebMvcTest(controllers = RoleController.class)
@Import(PermissionAspect.class)
// @WebMvcTest 的切片里没有 AOP 自动配置：不显式导入的话，@Aspect 只是个普通 Bean，
// 切面不生效、所有请求都放行——而测试会因为"没报错"看起来是绿的。
@ImportAutoConfiguration(AopAutoConfiguration.class)
class RolePermissionBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleService roleService;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void stubService() {
        when(roleService.list()).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void clearThreadLocal() {
        UserContext.clear();
    }

    @Test
    @DisplayName("作者调角色列表 → 403，且业务层一行都没执行")
    void authorIsForbidden() throws Exception {
        loginAs(2L, "author", Perm.ARTICLE_READ, Perm.ARTICLE_CREATE);

        mockMvc.perform(get("/api/roles").sessionAttr(SessionKeys.LOGIN_USER_ID, 2L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        verifyNoInteractions(roleService);
    }

    @Test
    @DisplayName("role:manage 可以读角色列表")
    void roleManagerCanRead() throws Exception {
        loginAs(1L, "admin", Perm.ROLE_MANAGE);

        mockMvc.perform(get("/api/roles").sessionAttr(SessionKeys.LOGIN_USER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("user:manage 也能读——注解是 ANY 语义，不是只认 role:manage")
    void userManagerCanAlsoRead() throws Exception {
        loginAs(3L, "auditor", Perm.USER_MANAGE);

        mockMvc.perform(get("/api/roles").sessionAttr(SessionKeys.LOGIN_USER_ID, 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("未登录 → 401，不是 403")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        verifyNoInteractions(roleService);
    }

    @Test
    @DisplayName("权限接口同样受控：作者读不到全部权限点")
    void permissionsEndpointIsAlsoGuarded() throws Exception {
        loginAs(2L, "author", Perm.ARTICLE_READ);

        mockMvc.perform(get("/api/roles/permissions").sessionAttr(SessionKeys.LOGIN_USER_ID, 2L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        verifyNoInteractions(roleService);
    }

    private void loginAs(Long id, String username, String... permissions) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setUsername(username);
        Set<String> set = new HashSet<>();
        for (String p : permissions) {
            set.add(p);
        }
        user.setPermissions(set);
        when(authService.loadLoginUser(id)).thenReturn(user);
    }
}
