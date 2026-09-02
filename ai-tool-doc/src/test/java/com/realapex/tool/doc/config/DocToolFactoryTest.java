package com.realapex.tool.doc.config;

import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.security.ToolSecurityInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DocToolFactory} 单元测试——工具组挂载与拦截器创建。
 */
class DocToolFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createDocTools_shouldReturnThreeTools() {
        DocToolConfig config = DocToolConfig.builder()
                .baseDir(tempDir)
                .outputDir(tempDir.resolve("output"))
                .build();

        List<AgentTool<?, ?>> tools = DocToolFactory.createDocTools(config);

        assertEquals(3, tools.size());
        List<String> names = tools.stream().map(AgentTool::name).toList();
        assertTrue(names.contains("read_and_convert_doc"), "应包含 read_and_convert_doc");
        assertTrue(names.contains("inspect_template_schema"), "应包含 inspect_template_schema");
        assertTrue(names.contains("render_document"), "应包含 render_document");
    }

    @Test
    void createPathInterceptor_shouldReturnInterceptorWithPriority5() {
        DocToolConfig config = DocToolConfig.builder()
                .baseDir(tempDir)
                .build();

        ToolSecurityInterceptor interceptor = DocToolFactory.createPathInterceptor(config);

        assertNotNull(interceptor);
        assertEquals(5, interceptor.priority());
    }
}