package com.ylf.cms.security.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ylf.cms.security.user.entity.SysRole;
import com.ylf.cms.security.user.vo.RolePermRow;
import com.ylf.cms.security.user.vo.RoleUserCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("select r.* from sys_role r join sys_user_role ur on ur.role_id = r.id "
            + "where ur.user_id = #{userId} and r.deleted = 0 order by r.sort asc, r.id asc")
    List<SysRole> selectByUserId(@Param("userId") Long userId);

    @Select("select count(1) from sys_user_role where role_id = #{roleId}")
    int countUsersOfRole(@Param("roleId") Long roleId);

    @Select("<script>"
            + "select role_id as roleId, perm_id as permId from sys_role_permission where role_id in "
            + "<foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<RolePermRow> selectPermRowsByRoleIds(@Param("roleIds") List<Long> roleIds);

    @Select("select role_id as roleId, count(1) as userCount from sys_user_role group by role_id")
    List<RoleUserCountRow> selectUserCounts();
}
