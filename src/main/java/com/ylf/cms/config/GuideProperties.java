package com.ylf.cms.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Data
@Component

public class GuideProperties {

    /**
     * 意图命中阈值。低于它的结果视为"没找到"，
     * 宁可回答"系统没有这个功能"，也不要把不相关的卡片硬塞给用户。
     */
    private double minScore = 0.15;

    private Llm llm = new Llm();

    @Autowired
    public void configure(Environment env) {
        minScore = env.getProperty("app.guide.min-score", Double.class, 0.15);
        llm.setEnabled(env.getProperty("app.guide.llm.enabled", Boolean.class, true));
        llm.setBaseUrl(env.getProperty("app.guide.llm.base-url", llm.getBaseUrl()));
        llm.setModel(env.getProperty("app.guide.llm.model", llm.getModel()));
        llm.setApiKey(env.getProperty("DEEPSEEK_API_KEY", ""));
        llm.setTimeoutMs(env.getProperty("app.guide.llm.timeout-ms", Long.class, 8000L));
    }

    @Data
    public static class Llm {
        /**
         * 通道总开关。打开不代表一定会调用模型：还要有密钥
         * （界面里填的会话密钥，或 apiKey 配置/环境变量）。
         */
        private boolean enabled = true;
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
        /** 服务端兜底密钥；为空时由用户在引导对话框里填写自己的 key */
        private String apiKey = "";
        private long timeoutMs = 8000;
    }
}
