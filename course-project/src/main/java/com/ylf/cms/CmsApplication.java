package com.ylf.cms;

import com.ylf.cms.config.WebMvcConfig;
import com.ylf.cms.config.TraceIdFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;
import org.springframework.web.filter.CharacterEncodingFilter;
import javax.servlet.*;

/** Servlet 3 初始化入口。由外置 Tomcat 加载，不创建内嵌服务器。 */
public class CmsApplication extends AbstractAnnotationConfigDispatcherServletInitializer {
    @Override protected Class<?>[] getRootConfigClasses() { return null; }
    @Override protected Class<?>[] getServletConfigClasses() { return new Class<?>[]{WebMvcConfig.class}; }
    @Override protected String[] getServletMappings() { return new String[]{"/"}; }
    @Override protected Filter[] getServletFilters() {
        return new Filter[]{new CharacterEncodingFilter("UTF-8", true), new TraceIdFilter()};
    }
    @Override public void onStartup(ServletContext context) throws ServletException {
        super.onStartup(context);
        context.setSessionTimeout(30);
        context.getSessionCookieConfig().setHttpOnly(true);
        context.setSessionTrackingModes(java.util.Collections.singleton(SessionTrackingMode.COOKIE));
    }
}
