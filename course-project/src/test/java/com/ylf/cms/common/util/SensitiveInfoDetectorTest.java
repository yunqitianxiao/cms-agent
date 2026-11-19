package com.ylf.cms.common.util;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SensitiveInfoDetectorTest {
    private void detected(String type,String value) {
        List<SensitiveInfoDetector.Hit> hits=SensitiveInfoDetector.detect("content",value);
        assertEquals(1,hits.size()); assertEquals(type,hits.get(0).getType());
        assertEquals("***",SensitiveInfoDetector.mask(value));
    }
    @Test void findsMobileNumber() { detected("手机号","13800138000"); }
    @Test void validatesIdentityChecksum() { detected("身份证","11010519491231002X"); }
    @Test void validatesBankCardLuhn() { detected("银行卡","4111111111111111"); }
    @Test void findsEmail() { detected("邮箱","reader@example.com"); }
    @Test void findsSyntheticApiKey() { detected("API Key","sk-demonstration-only-00000000"); }
    @Test void findsPlainPassword() { detected("明文口令","密码：test-only-123"); }
    @Test void ignoresOrdinaryTextAndBadChecksums() {
        assertTrue(SensitiveInfoDetector.detect("x","稿件共 18 篇，订单 110105194912310020").isEmpty());
        assertTrue(SensitiveInfoDetector.detect("x",null).isEmpty());
    }
    @Test void mergesOverlappingMatchesWithoutExposingOriginal() {
        String value="password=13800138000";
        assertEquals(1,SensitiveInfoDetector.detect("content",value).size());
        assertEquals("***",SensitiveInfoDetector.mask(value));
        assertFalse(SensitiveInfoDetector.detect("content",value).toString().contains("13800138000"));
    }
    @Test void masksNestedLogsAndNumberFieldsBeforeTruncation() {
        Map<String,Object> input=Map.of("nested",List.of(Map.of("api_key","fake-demo-secret","phone",13800138000L)),"content","联系 reader@example.com");
        String masked=MaskUtil.mask(new Object[]{input},new ObjectMapper());
        assertFalse(masked.contains("fake-demo-secret"));
        assertFalse(masked.contains("13800138000"));
        assertFalse(masked.contains("reader@example.com"));
        assertTrue(masked.contains("***"));
    }
}
