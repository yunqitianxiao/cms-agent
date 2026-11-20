package com.ylf.cms.security.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联。对应 sys_user_role。
 *
 * 这张表是 (user_id, role_id) 复合主键，没有自增 id，也没有 create_time：
 * 谁在什么时候被授了哪个角色，由 sys_log 记录，关联表只保存"当前归属"。
 * 读写走 {@link com.ylf.cms.security.user.mapper.SysUserRoleMapper} 里的注解 SQL，
 * 不用 BaseMapper 那套按主键增删改。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    private Long userId;

    private Long roleId;
}
