package com.ylf.cms.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableAspectJAutoProxy(proxyTargetClass=true)
@PropertySource("classpath:cms.properties")
@MapperScan(basePackages="com.ylf.cms", annotationClass=org.apache.ibatis.annotations.Mapper.class)
public class PersistenceConfig {
    @Bean(destroyMethod="close") public HikariDataSource dataSource(Environment env) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setJdbcUrl(env.getProperty("CMS_DB_URL", env.getProperty("jdbc.url")));
        ds.setUsername(env.getProperty("CMS_DB_USERNAME", env.getProperty("jdbc.username")));
        String password = env.getProperty("CMS_DB_PASSWORD", env.getProperty("jdbc.password"));
        if (ds.getJdbcUrl() == null || ds.getUsername() == null || password == null || password.trim().isEmpty()) {
            throw new IllegalStateException("请设置 CMS_DB_URL、CMS_DB_USERNAME 和 CMS_DB_PASSWORD 环境变量");
        }
        ds.setPassword(password);
        ds.setMaximumPoolSize(10); ds.setMinimumIdle(2); ds.setConnectionTimeout(5000);
        return ds;
    }
    @Bean public SqlSessionFactory sqlSessionFactory(DataSource ds, MybatisPlusInterceptor interceptor,
                                                    AuditMetaObjectHandler audit) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(ds);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/**/*.xml"));
        bean.setTypeAliasesPackage("com.ylf.cms");
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        cfg.setLogImpl(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);
        bean.setConfiguration(cfg);
        GlobalConfig.DbConfig db = new GlobalConfig.DbConfig();
        db.setIdType(IdType.AUTO); db.setLogicDeleteField("deleted");
        db.setLogicDeleteValue("1"); db.setLogicNotDeleteValue("0");
        GlobalConfig global = new GlobalConfig(); global.setBanner(false);
        global.setDbConfig(db); global.setMetaObjectHandler(audit);
        bean.setGlobalConfig(global); bean.setPlugins(interceptor);
        return bean.getObject();
    }
    @Bean public PlatformTransactionManager transactionManager(DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
