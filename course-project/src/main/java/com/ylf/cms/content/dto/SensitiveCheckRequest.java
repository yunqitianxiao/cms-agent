package com.ylf.cms.content.dto;
import lombok.Data;
import javax.validation.constraints.Size;
@Data public class SensitiveCheckRequest {
 @Size(max=200) private String title;
 @Size(max=500) private String summary;
 @Size(max=200000) private String content;
}
