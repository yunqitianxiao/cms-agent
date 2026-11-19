package com.ylf.cms.common.util;

import lombok.Value;
import java.util.*;
import java.util.regex.*;
import java.util.function.Predicate;

/** 只向调用方公开位置、类型和掩码，不保存或返回原文。 */
public final class SensitiveInfoDetector {
    private SensitiveInfoDetector() {}

    @Value
    public static class Hit {
        String field;
        String type;
        int start;
        int end;
        public String getMasked() { return "***"; }
    }

    private static final class Rule {
        final Pattern pattern;
        final String type;
        final Predicate<String> valid;
        Rule(String type, String regex, Predicate<String> valid) {
            this.type = type;
            this.pattern = Pattern.compile(regex);
            this.valid = valid;
        }
    }
    private static final Predicate<String> ALWAYS = value -> true;
    private static final List<Rule> RULES = List.of(
        new Rule("明文口令", "(?i)(?:password|passwd|pwd|密码|口令)[\"\']?\\s*[:=：]\\s*[\"\']?[^\\s\"\'，,;；。}]+", ALWAYS),
        new Rule("API Key", "(?i)(?:\\bsk-[a-z0-9_-]{16,}|(?:api[-_ ]?key|access[-_ ]?token)[\"\']?\\s*[:=：]\\s*[\"\']?[a-z0-9_./+=-]+)", ALWAYS),
        new Rule("邮箱", "(?i)[a-z0-9._%+\\-]{1,64}@[a-z0-9.-]{1,253}\\.[a-z]{2,63}", ALWAYS),
        new Rule("身份证", "(?<![0-9])[1-9][0-9]{16}[0-9Xx](?![0-9])", SensitiveInfoDetector::validId),
        new Rule("银行卡", "(?<![0-9])[1-9][0-9]{12,18}(?![0-9])", SensitiveInfoDetector::validCard),
        new Rule("手机号", "(?<![0-9])1[3-9][0-9]{9}(?![0-9])", ALWAYS)
    );

    private static boolean validId(String value) {
        int[] weights = {7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2};
        int sum = 0;
        for (int i = 0; i < weights.length; i++) sum += (value.charAt(i) - '0') * weights[i];
        return "10X98765432".charAt(sum % 11) == Character.toUpperCase(value.charAt(17));
    }
    private static boolean validCard(String value) {
        int sum = 0;
        boolean doubleNext = false;
        for (int i = value.length()-1; i >= 0; i--) {
            int n = value.charAt(i) - '0';
            if (doubleNext) { n *= 2; if (n > 9) n -= 9; }
            sum += n;
            doubleNext = !doubleNext;
        }
        return sum % 10 == 0;
    }

    public static List<Hit> detect(String field, String text) {
        List<Hit> hits = new ArrayList<>();
        if (text == null || text.isEmpty()) return hits;
        // 同一字段内的相交部分合并，日志替换时覆盖整个并集。
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(text);
            while (matcher.find()) {
                if (rule.valid.test(matcher.group())) {
                    hits.add(new Hit(field, rule.type, matcher.start(), matcher.end()));
                }
            }
        }
        hits.sort(Comparator.comparingInt(Hit::getStart));
        List<Hit> merged = new ArrayList<>();
        for (Hit hit : hits) {
            int i = merged.size()-1;
            if (i < 0 || merged.get(i).getEnd() <= hit.getStart()) {
                merged.add(hit);
            } else {
                Hit first = merged.get(i);
                merged.set(i, new Hit(field, first.getType(), first.getStart(), Math.max(first.getEnd(), hit.getEnd())));
            }
        }
        return merged;
    }
    public static String mask(String text) {
        if (text == null) return null;
        StringBuilder out = new StringBuilder();
        int last = 0;
        for (Hit hit : detect("", text)) {
            out.append(text, last, hit.getStart()).append("***");
            last = hit.getEnd();
        }
        return out.append(text, last, text.length()).toString();
    }
}
