package com.ylf.cms.security.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限点。对应 sys_permission。
 *
 * perm_code 是全站契约：切面按它判权、站点地图按它校验卡片，改名等于改接口，
 * 已经发出去的码只增不减。
 *
 * 这张表由初始化脚本写入，运行期只读，所以没有 status / deleted：
 * 停用一个权限点要改的是代码里的常量，而不是在库里悄悄改一行——
 * 后者会让"权限码集合是稳定的"这个前提失效。
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String permCode;

    private String permName;

    /** 功能分组：内容 / 审核 / 分类 / 账号与权限 / 系统 */
    private String permGroup;

    private String description;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
