package com.ylf.cms.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文章状态枚举（M2）。
 *
 * 流转规则（Service 层严格按此校验，非法流转返回 40901）：
 *   DRAFT    --提交审核-->  PENDING
 *   PENDING  --审核通过-->  PUBLISHED
 *   PENDING  --审核驳回-->  REJECTED（必须带审核意见）
 *   REJECTED --编辑/重新提交--> PENDING
 *   PUBLISHED--下架-->      OFFLINE（必须带下架原因）
 *   OFFLINE  --重新上架(仅管理员)--> PUBLISHED
 *
 * 禁止：DRAFT 直接审核通过、PUBLISHED 直接退回 DRAFT。
 *
 * 库表 status 为 VARCHAR(16)，枚举名直接落库：M3 前台按字符串过滤时
 * 不需要再查数字码对照表，跨模块语义一致。
 */
@Getter
@AllArgsConstructor
public enum ArticleStatusEnum {

    DRAFT("DRAFT", "草稿"),
    PENDING("PENDING", "待审核"),
    PUBLISHED("PUBLISHED", "已发布"),
    REJECTED("REJECTED", "已驳回"),
    OFFLINE("OFFLINE", "已下架");

    /** 落库值 */
    private final String code;

    /** 中文描述（VO 的 statusDesc 直接取它） */
    private final String desc;

    public static ArticleStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (ArticleStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 是否是"作者可以编辑"的状态 */
    public boolean editable() {
        return this == DRAFT || this == REJECTED;
    }

    /** 是否是"前台可见"的状态 */
    public boolean visible() {
        return this == PUBLISHED;
    }
}
