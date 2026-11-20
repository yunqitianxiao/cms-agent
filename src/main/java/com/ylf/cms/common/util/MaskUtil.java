package com.ylf.cms.common.util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
/** 递归按字段名脱敏，再按内容打码，最后截断；序列化失败不记录原文。 */
public final class MaskUtil {
 private MaskUtil() {}
 private static final Set<String> KEYS = new HashSet<>(Arrays.asList("password","oldpassword","newpassword","confirmpassword","passwd","pwd","token","apikey","secret","credential","authorization","cookie"));
 public static String mask(Object[] args,ObjectMapper mapper) {
  if(args==null) return "";
  List<String> parts=new ArrayList<>();
  for(Object arg:args) {
   if(arg==null || arg instanceof javax.servlet.ServletRequest || arg instanceof javax.servlet.ServletResponse || arg instanceof javax.servlet.http.HttpSession || arg instanceof org.springframework.ui.Model) continue;
   try { parts.add(mapper.writeValueAsString(clean(mapper.valueToTree(arg)))); }
   catch(Exception e) { parts.add("[参数无法序列化，未记录原文]"); }
  }
  String text=String.join(" | ",parts);
  return text.length()>1500 ? text.substring(0,1500)+"...(截断)" : text;
 }
 private static JsonNode clean(JsonNode node) {
  if(node.isObject()) {
   ObjectNode object=(ObjectNode)node;
   List<String> names=new ArrayList<>();object.fieldNames().forEachRemaining(names::add);
   for(String name:names) {
    String key=name.toLowerCase(Locale.ROOT).replaceAll("[-_ ]", "");
    object.set(name,KEYS.contains(key)?TextNode.valueOf("***"):clean(object.get(name)));
   }
  } else if(node.isArray()) {
   ArrayNode array=(ArrayNode)node;
   for(int i=0;i<array.size();i++) array.set(i,clean(array.get(i)));
  } else if(node.isTextual() || node.isNumber()) {
   String text=node.asText(),safe=SensitiveInfoDetector.mask(text);
   if(!text.equals(safe)) return TextNode.valueOf(safe);
  }
  return node;
 }
}
