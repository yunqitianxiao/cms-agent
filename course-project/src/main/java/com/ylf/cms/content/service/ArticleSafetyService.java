package com.ylf.cms.content.service;
import com.ylf.cms.common.util.SensitiveInfoDetector;
import com.ylf.cms.common.exception.BizException;
import com.ylf.cms.common.result.ResultCode;
import com.ylf.cms.content.dto.ArticleSaveRequest;
import org.springframework.stereotype.Service;
import java.util.*;
@Service public class ArticleSafetyService {
 public List<SensitiveInfoDetector.Hit> check(String title,String summary,String content) {
  List<SensitiveInfoDetector.Hit> hits=new ArrayList<>();
  hits.addAll(SensitiveInfoDetector.detect("title",title));
  hits.addAll(SensitiveInfoDetector.detect("summary",summary));
  hits.addAll(SensitiveInfoDetector.detect("content",content));return hits;
 }
 public void requireSafe(ArticleSaveRequest request) {
  if(!check(request.getTitle(),request.getSummary(),request.getContent()).isEmpty()) throw new BizException(ResultCode.SENSITIVE_INFO);
 }
}
