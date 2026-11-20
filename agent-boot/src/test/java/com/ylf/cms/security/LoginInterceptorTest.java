package com.ylf.cms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylf.cms.common.constant.SessionKeys;
import com.ylf.cms.common.exception.BizException;
import com.ylf.cms.common.result.ResultCode;
import com.ylf.cms.security.user.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D5 的判据：未登录访问 /api/** 返回 401。
 *
 * 这条测试能失败：把 LoginInterceptor.reject 里给响应写 Result 的那几行删掉，
 * noSessionIsRejected 会立刻变红（status 仍是 401，但 body 为空）。
 */
class LoginInterceptorTest {

    private final AuthService authService = mock(AuthService.class);
    private final LoginInterceptor interceptor = new LoginInterceptor(authService, new ObjectMapper());

    @AfterEach
    void clearThreadLocal() {
        // 拦截器不清理时会污染同线程的下一个用例，这里兜底
        UserContext.clear();
    }

    @Test
    @DisplayName("没有 Session 访问 /api/** 直接 401，且不穿透到数据库")
    void noSessionIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, null);

        assertFalse(allowed, "未登录请求不该进入 Controller");
        assertEquals(401, response.getStatus());
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"code\":40100"), "响应体应带 UNAUTHORIZED 错误码，实际：" + body);
        // 连 userId 都没有，不该去查库——否则未登录请求会把数据库当身份源反复打
        verifyNoInteractions(authService);
        assertNull(UserContext.get(), "被拒绝的请求不该留下上下文");
    }

    @Test
    @DisplayName("Session 里的用户已停用：401 且旧 Session 立即作废")
    void disabledUserInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.LOGIN_USER_ID, 7L);
        when(authService.loadLoginUser(7L)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/articles");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, null));

        assertEquals(401, response.getStatus());
        assertTrue(session.isInvalid(), "身份失效后必须作废旧 Session，不能留半死状态");
        verify(authService).loadLoginUser(7L);
    }

    @Test
    @DisplayName("身份有效：放行并把 LoginUser 放进 ThreadLocal，请求结束后清空")
    void validUserIsLoadedAndCleared() throws Exception {
        LoginUser user = new LoginUser();
        user.setId(7L);
        user.setUsername("alice");
        when(authService.loadLoginUser(7L)).thenReturn(user);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.LOGIN_USER_ID, 7L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/articles");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, null));
        assertSame(user, UserContext.get());

        interceptor.afterCompletion(request, response, null, null);
        // 不清理的话，Tomcat 复用线程会让下一个请求读到上一个用户的身份（偶发越权）
        assertNull(UserContext.get(), "请求结束后必须清空上下文");
    }

    @Test
    @DisplayName("非 /api 路径失败时跳登录页，并把原始地址带在 next 里")
    void pageRequestRedirectsToLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        request.setQueryString("tab=1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, null));

        assertEquals(302, response.getStatus());
        assertEquals("/login?next=%2Fdashboard%3Ftab%3D1", response.getRedirectedUrl());
    }

    @Test
    @DisplayName("UserContext.require 在无登录态时抛 UNAUTHORIZED")
    void requireWithoutUserThrowsUnauthorized() {
        UserContext.clear();

        BizException ex = assertThrows(BizException.class, UserContext::require);
        assertEquals(ResultCode.UNAUTHORIZED, ex.getResultCode());
    }
}
