package com.ylf.cms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylf.cms.common.constant.SessionKeys;
import com.ylf.cms.common.exception.BizException;
import com.ylf.cms.common.result.ResultCode;
import com.ylf.cms.security.user.entity.SysUser;
import com.ylf.cms.security.user.mapper.SysRoleMapper;
import com.ylf.cms.security.user.mapper.SysUserMapper;
import com.ylf.cms.security.user.mapper.SysUserRoleMapper;
import com.ylf.cms.security.user.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 判据：账号被停用后，旧会话再发请求要回 40102，而不是混成 40100。
 *
 * 能失败：把 LoginInterceptor 里 catch BizException 那段删掉（让异常直接冒泡），
 * 或者让 AuthService.loadLoginUser 对停用账号仍返回 null，下面第一条断言立刻变红。
 */
class AccountDisabledBoundaryTest {

    private final AuthService authService = mock(AuthService.class);
    private final LoginInterceptor interceptor = new LoginInterceptor(authService, new ObjectMapper());

    @AfterEach
    void clearThreadLocal() {
        UserContext.clear();
    }

    @Test
    @DisplayName("账号被停用：40102 + 旧 Session 作废，且不留下登录上下文")
    void disabledAccountReturns40102() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionKeys.LOGIN_USER_ID, 9L);
        when(authService.loadLoginUser(9L)).thenThrow(new BizException(ResultCode.ACCOUNT_DISABLED));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/articles");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, null), "停用账号不该进入 Controller");

        assertEquals(401, response.getStatus());
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"code\":40102"), "应回 ACCOUNT_DISABLED(40102)，实际：" + body);
        assertFalse(body.contains("\"code\":40100"), "停用不能混成未登录：" + body);
        assertTrue(session.isInvalid(), "停用后必须作废旧 Session");
        assertNull(UserContext.get(), "被拒绝的请求不该留下上下文");
    }

    @Test
    @DisplayName("AuthService 分得清：停用抛 40102，已删除仍返回 null")
    void serviceSeparatesDisabledFromDeleted() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        AuthService service = new AuthService(userMapper, mock(SysRoleMapper.class),
                mock(SysUserRoleMapper.class), mock(PasswordEncoder.class));

        SysUser disabled = new SysUser();
        disabled.setId(9L);
        disabled.setUsername("bob");
        disabled.setStatus(0);
        when(userMapper.selectById(9L)).thenReturn(disabled);

        BizException ex = assertThrows(BizException.class, () -> service.loadLoginUser(9L));
        assertEquals(ResultCode.ACCOUNT_DISABLED, ex.getResultCode());

        when(userMapper.selectById(404L)).thenReturn(null);
        assertNull(service.loadLoginUser(404L), "被删除的账号仍是 40100，不能和停用混成一个码");
    }
}
