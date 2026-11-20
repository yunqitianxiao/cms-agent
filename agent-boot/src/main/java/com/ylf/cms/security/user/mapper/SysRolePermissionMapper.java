package com.ylf.cms.security.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ylf.cms.security.user.entity.SysRolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关联。复合主键表，只用 BaseMapper 里按条件的那几个方法：
 * selectList / selectCount / insert / delete(Wrapper)。
 * selectById、updateById、deleteById 依赖单列主键，这张表上没有，别调。
 *
 * 下面三个是按复合主键封的便捷方法。注意插入不叫 insert：BaseMapper 已经
 * 注入了一个叫 insert 的语句，同名声明会撞 statement id；而且这里要的是
 * "已经存在就不动"的幂等插入，和 BaseMapper 遇到重复键抛异常不是一回事。
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    @Insert("insert ignore into sys_role_permission(role_id, perm_id) values(#{roleId}, #{permId})")
    int insertIgnore(@Param("roleId") Long roleId, @Param("permId") Long permId);

    @Delete("delete from sys_role_permission where role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Select("select perm_id from sys_role_permission where role_id = #{roleId}")
    List<Long> selectPermIds(@Param("roleId") Long roleId);
}
