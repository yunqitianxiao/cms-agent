package com.ylf.cms.security.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ylf.cms.security.user.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限点。运行期只读，用的都是 BaseMapper 自带的查询方法。
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
}
