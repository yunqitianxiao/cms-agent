package com.ylf.cms.security.user.vo;

import lombok.Data;

import java.util.List;

@Data
public class RoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer sort;
    private Integer status;
    private List<Long> permIds;
    private Integer userCount;
}
