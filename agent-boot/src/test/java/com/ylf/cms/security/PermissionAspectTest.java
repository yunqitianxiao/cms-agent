package com.ylf.cms.security;

import com.ylf.cms.common.annotation.RequirePermission;
import com.ylf.cms.common.constant.Perm;
import com.ylf.cms.common.exception.BizException;
import com.ylf.cms.common.result.ResultCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D6 的判据（单元层）：接口级权限校验必须"没有权限就不进入方法"，
 * 而不是"方法执行完再返回 403"——后者意味着写操作已经落库了。
 *
 * 这条测试能失败：把 PermissionAspect 里的 allowed 判断改成恒 true，
 * authorIsDeniedBeforeMethodRuns 立刻变红（proceed 被调用了）。
 */
class PermissionAspectTest {

    private final PermissionAspect aspect = new PermissionAspect();

    @AfterEach
    void clearThreadLocal() {
        UserContext.clear();
    }

    @Test
    @DisplayName("作者没有 article:audit → 403，且被拦的方法一次都没执行")
    void authorIsDeniedBeforeMethodRuns() throws Throwable {
        login(Perm.ARTICLE_READ, Perm.ARTICLE_CREATE);
        ProceedingJoinPoint pjp = joinPoint("ok");

        BizException e = assertThrows(BizException.class,
                () -> aspect.check(pjp, annotation("audit")));

        assertEquals(ResultCode.FORBIDDEN, e.getResultCode());
        assertTrue(e.getMessage().contains(Perm.ARTICLE_AUDIT),
                "提示里应带出缺的是哪个权限码，实际：" + e.getMessage());
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("有权限时放行，并原样返回业务方法的返回值")
    void permittedCallProceeds() throws Throwable {
        login(Perm.ARTICLE_AUDIT);
        ProceedingJoinPoint pjp = joinPoint("audit-done");

        assertEquals("audit-done", aspect.check(pjp, annotation("audit")));
        verify(pjp).proceed();
    }

    @Test
    @DisplayName("多个权限码默认 ANY：命中一个就放行")
    void anyLogicPassesWithOne() throws Throwable {
        login(Perm.USER_MANAGE);
        ProceedingJoinPoint pjp = joinPoint("ok");

        aspect.check(pjp, annotation("anyOfTwo"));
        verify(pjp).proceed();
    }

    @Test
    @DisplayName("ALL 语义：两个权限码缺一个就拒绝")
    void allLogicRequiresEveryCode() throws Throwable {
        login(Perm.USER_MANAGE);
        ProceedingJoinPoint pjp = joinPoint("ok");

        BizException e = assertThrows(BizException.class,
                () -> aspect.check(pjp, annotation("allOfTwo")));

        assertEquals(ResultCode.FORBIDDEN, e.getResultCode());
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("未登录身份调用受控接口 → 401，不是当成「没有权限」的 403")
    void anonymousIsUnauthorized() throws Throwable {
        ProceedingJoinPoint pjp = joinPoint("ok");

        BizException e = assertThrows(BizException.class,
                () -> aspect.check(pjp, annotation("audit")));

        assertEquals(ResultCode.UNAUTHORIZED, e.getResultCode());
        verify(pjp, never()).proceed();
    }

    private void login(String... permissions) {
        LoginUser user = new LoginUser();
        user.setId(1L);
        user.setUsername("author");
        Set<String> set = new HashSet<>();
        for (String p : permissions) {
            set.add(p);
        }
        user.setPermissions(set);
        UserContext.set(user);
    }

    private ProceedingJoinPoint joinPoint(Object result) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("ProbeController.probe()");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.proceed()).thenReturn(result);
        return pjp;
    }

    /** 从真实注解上取实例，顺带证明 @RequirePermission 是 RUNTIME 可见的。 */
    private RequirePermission annotation(String method) throws NoSuchMethodException {
        Method m = Probe.class.getDeclaredMethod(method);
        return m.getAnnotation(RequirePermission.class);
    }

    @SuppressWarnings("unused")
    private static class Probe {

        @RequirePermission(Perm.ARTICLE_AUDIT)
        void audit() {
        }

        @RequirePermission({Perm.ROLE_MANAGE, Perm.USER_MANAGE})
        void anyOfTwo() {
        }

        @RequirePermission(value = {Perm.ROLE_MANAGE, Perm.USER_MANAGE},
                logic = RequirePermission.Logic.ALL)
        void allOfTwo() {
        }
    }
}
