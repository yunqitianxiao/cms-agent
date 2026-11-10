package com.ylf.cms.security.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ylf.cms.security.LoginUser;
import com.ylf.cms.common.constant.RoleCode;
import com.ylf.cms.common.exception.BizException;
import com.ylf.cms.common.result.ResultCode;
import com.ylf.cms.security.user.dto.RegisterRequest;
import com.ylf.cms.security.user.entity.SysRole;
import com.ylf.cms.security.user.entity.SysUser;
import com.ylf.cms.security.user.mapper.SysRoleMapper;
import com.ylf.cms.security.user.mapper.SysUserMapper;
import com.ylf.cms.security.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 校验账号密码。
     *
     * 账号不存在和密码错误返回同一个错误码：分开提示等于给攻击者一个
     * 用户名枚举接口，能批量判断哪些账号真实存在。
     */
    public LoginUser authenticate(String username, String rawPassword) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        String encoded = userMapper.selectPasswordByUsername(username);
        if (encoded == null || !passwordEncoder.matches(rawPassword, encoded)) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        return toLoginUser(user);
    }

    /**
     * 每请求重新装载登录用户。
     *
     * 两种失效分开表达，拦截器据此回不同的码：
     * - 用户已被删除 → 返回 null，回 40100 未登录；
     * - 用户已被停用 → 抛 40102，前端能提示"账号已被停用"，而不是再跳一次登录页。
     *
     * 混成一个 null 的话，用户拿正确口令反复登录也只会看到"未登录"，
     * 真正的原因（管理员停用了他）永远露不出来。
     */
    public LoginUser loadLoginUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        return toLoginUser(user);
    }

    @Transactional
    public Long register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.DUPLICATE, "用户名已被占用");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        userMapper.insert(user);

        SysRole defaultRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, RoleCode.DEFAULT));
        if (defaultRole != null) {
            userRoleMapper.insert(user.getId(), defaultRole.getId());
        }
        return user.getId();
    }

    private LoginUser toLoginUser(SysUser user) {
        LoginUser lu = new LoginUser();
        lu.setId(user.getId());
        lu.setUsername(user.getUsername());
        lu.setNickname(user.getNickname());
        List<String> roleCodes = userMapper.selectRoleCodes(user.getId());
        List<String> perms = userMapper.selectPermCodes(user.getId());
        lu.setRoleCodes(new HashSet<>(roleCodes));
        lu.setPermissions(new HashSet<>(perms));
        return lu;
    }

    public Set<String> permissionsOf(Long userId) {
        return new HashSet<>(userMapper.selectPermCodes(userId));
    }
}
