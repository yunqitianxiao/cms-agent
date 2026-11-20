package com.ylf.cms.ops.guide.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class SiteMapYaml {

    private Map<String, GuideCopy> cards = new LinkedHashMap<>();
}
