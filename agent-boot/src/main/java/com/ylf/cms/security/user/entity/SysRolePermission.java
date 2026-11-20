package com.ylf.cms.security.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限关联。对应 sys_role_permission。
 *
 * 与 sys_user_role 同样是复合主键，改授权就是删旧行插新行，
 * 不做"标记失效再过滤"——少一种漏过滤的可能。
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    private Long roleId;

    private Long permId;
}
