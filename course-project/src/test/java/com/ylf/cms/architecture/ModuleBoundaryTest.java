package com.ylf.cms.architecture;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ModuleBoundaryTest {
    static final Set<String> DOMAINS=Set.of("security","content","catalog","ops");
    static final Pattern IMPORT=Pattern.compile("(?m)^import\\s+(?:static\\s+)?com\\.ylf\\.cms\\.([\\w.]+)\\s*;");
    static final Pattern PACKAGE=Pattern.compile("(?m)^package\\s+com\\.ylf\\.cms\\.([\\w.]+);");
    static List<String> violations(String source) {
        List<String> errors=new ArrayList<>();
        Matcher declaration=PACKAGE.matcher(source);
        if(!declaration.find()) return errors;
        String domain=declaration.group(1).split("\\.")[0];
        if(!DOMAINS.contains(domain)) return errors;
        Matcher imports=IMPORT.matcher(source);
        while(imports.find()) {
            String[] parts=imports.group(1).split("\\.");
            if(!DOMAINS.contains(parts[0]) || parts[0].equals(domain)) continue;
            List<String> segments=Arrays.asList(parts);
            if(segments.stream().anyMatch(Set.of("mapper","entity","controller","config")::contains)) errors.add(imports.group(1));
        }
        return errors;
    }
    @Test void productionSourcesRespectDomainBoundaries() throws Exception {
        List<String> errors=new ArrayList<>();int count=0;
        try(Stream<Path> files=Files.walk(Paths.get("src/main/java"))) {
            for(Path file:(Iterable<Path>)files.filter(p->p.toString().endsWith(".java"))::iterator) {
                count++;for(String error:violations(Files.readString(file))) errors.add(file+": "+error);
            }
        }
        assertTrue(count>100,"必须真的扫描到工程，不能空目录通过");
        assertTrue(errors.isEmpty(),String.join("\n",errors));
    }
    @Test void injectedNestedMapperViolationIsDetected() {
        String fixture="package com.ylf.cms.content.service;\nimport com.ylf.cms.security.user.mapper.SysUserMapper;\n";
        assertEquals(1,violations(fixture).size());
        assertTrue(violations(fixture.replace("mapper.SysUserMapper","service.UserService")).isEmpty());
    }
}
