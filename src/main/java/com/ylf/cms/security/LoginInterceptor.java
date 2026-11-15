package com.ylf.cms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylf.cms.common.constant.SessionKeys;
import com.ylf.cms.common.exception.BizException;
import com.ylf.cms.common.result.Result;
import com.ylf.cms.common.result.ResultCode;
import com.ylf.cms.security.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 登录拦截。
 *
 * Session 里只存 userId，每次请求回查数据库装载身份。代价是每个请求多两条查询，
 * 换来的是"停用账号立即生效、改角色立即生效"——课程演示里改完权限还要重新登录
 * 才能看到效果，会很难解释。
 *
 * 40100（未登录 / 账号已删除）与 40102（账号已停用）分开返回：
 * 前端据此决定跳登录页还是提示"账号已被停用"，混成一个码只能靠字符串猜。
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        Long userId = session == null ? null : (Long) session.getAttribute(SessionKeys.LOGIN_USER_ID);

        if (userId == null) {
            return reject(request, response, ResultCode.UNAUTHORIZED);
        }

        LoginUser user;
        try {
            user = authService.loadLoginUser(userId);
        } catch (BizException e) {
            // 账号被停用：旧 Session 立刻作废，并明确回 40102
            session.invalidate();
            return reject(request, response, e.getResultCode());
        }
        if (user == null) {
            // 用户已被删除：同样作废旧 Session，不留半死状态
            session.invalidate();
            return reject(request, response, ResultCode.UNAUTHORIZED);
        }
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理：Tomcat 复用线程，残留上下文会让下一个请求带着上一个用户的身份
        UserContext.clear();
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response, ResultCode code)
            throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            response.setStatus(code.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code)));
        } else {
            String next = request.getRequestURI();
            String query = request.getQueryString();
            if (query != null && !query.isEmpty()) {
                next = next + "?" + query;
            }
            response.sendRedirect("/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8.name()));
        }
        return false;
    }
}
