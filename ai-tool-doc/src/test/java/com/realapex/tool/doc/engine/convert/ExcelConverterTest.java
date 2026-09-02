package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.doc.model.DocConvertOptions;
import com.realapex.tool.doc.model.DocConvertResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExcelConverter} 单元测试——.xlsx SAX 流式解析为 Markdown 表格。
 */
class ExcelConverterTest {

    @TempDir
    Path tempDir;

    private Path createXlsx(String sheetName, String[][] data) throws Exception {
        Path file = tempDir.resolve("data.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(file)) {
            Sheet sheet = wb.createSheet(sheetName);
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < data[r].length; c++) {
                    row.createCell(c).setCellValue(data[r][c]);
                }
            }
            wb.write(out);
        }
        return file;
    }

    @Test
    void convert_shouldRenderSheetAndTable() throws Exception {
        String[][] data = {{"产品", "销量"}, {"A", "100"}, {"B", "200"}};
        Path file = createXlsx("销售数据", data);
        DocConvertResult result = new ExcelConverter().convert(file, DocConvertOptions.defaults());

        assertEquals("xlsx", result.getFormat());
        assertTrue(result.getMarkdown().contains("## 销售数据"), "应渲染 Sheet 标题");
        assertTrue(result.getMarkdown().contains("| 产品 | 销量 |"), "应渲染表头");
        assertTrue(result.getMarkdown().contains("| A | 100 |"), "应渲染数据行");
    }

    @Test
    void convert_shouldTruncateRowsBeyondMaxRows() throws Exception {
        String[][] data = new String[150][2];
        for (int r = 0; r < 150; r++) {
            data[r] = new String[]{"row" + r, String.valueOf(r)};
        }
        Path file = createXlsx("大数据", data);
        DocConvertOptions options = DocConvertOptions.builder().maxRows(100).build();
        DocConvertResult result = new ExcelConverter().convert(file, options);

        assertTrue(result.isTruncated(), "超过 maxRows 应标记截断");
        assertTrue(result.getMarkdown().contains("截断"), "应包含截断提示");
    }

    @Test
    void convert_shouldTruncateColsBeyondMaxCols() throws Exception {
        String[][] data = new String[3][60];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 60; c++) {
                data[r][c] = "c" + c;
            }
        }
        Path file = createXlsx("宽表", data);
        DocConvertOptions options = DocConvertOptions.builder().maxCols(50).build();
        DocConvertResult result = new ExcelConverter().convert(file, options);

        assertTrue(result.isTruncated(), "超过 maxCols 应标记截断");
    }
}