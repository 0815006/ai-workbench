package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.doc.model.DocConvertOptions;
import com.realapex.tool.doc.model.DocConvertResult;
import com.realapex.tool.doc.model.DocFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.eventusermodel.dummyrecord.LastCellOfRowDummyRecord;
import org.apache.poi.hssf.record.BOFRecord;
import org.apache.poi.hssf.record.BlankRecord;
import org.apache.poi.hssf.record.BoolErrRecord;
import org.apache.poi.hssf.record.BoundSheetRecord;
import org.apache.poi.hssf.record.FormulaRecord;
import org.apache.poi.hssf.record.LabelSSTRecord;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.RowRecord;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 转换器——将 .xls/.xlsx 转换为结构化 Markdown 表格。
 *
 * <p>防 OOM 设计：</p>
 * <ul>
 *   <li>.xlsx → POI SAX 流式解析（{@code XSSFReader} + {@code XSSFSheetXMLHandler}），严禁 DOM 全量加载</li>
 *   <li>.xls → HSSF Event API 流式解析（{@code HSSFEventFactory} + {@code HSSFListener}）</li>
 *   <li>强制截断：行数超过 {@code maxRows}（默认 100）、列数超过 {@code maxCols}（默认 50）</li>
 *   <li>公式单元格使用缓存值（{@code formulasNotResults=false}）</li>
 * </ul>
 *
 * <p>每个 Sheet 输出为一个 Markdown 表格块（以 {@code ## Sheet 名} 作为二级标题），
 * 首行作为表头并附加 {@code |---|} 分隔行。</p>
 */
@Slf4j
public class ExcelConverter implements DocumentConverter {

    private static final DataFormatter FORMATTER = new DataFormatter();

    @Override
    public String format() {
        return "excel";
    }

    @Override
    public boolean supports(DocFormat format) {
        return format == DocFormat.XLS || format == DocFormat.XLSX;
    }

    @Override
    public DocConvertResult convert(Path file, DocConvertOptions options) throws Exception {
        DocFormat format = FormatDetector.detect(file);
        if (format == DocFormat.XLS) {
            return convertLegacyXls(file, options);
        }
        return convertXlsx(file, options);
    }

    /**
     * 转换 .xlsx（XSSFReader SAX 流式解析）。
     *
     * @param file    .xlsx 文件
     * @param options 转换选项
     * @return 转换结果
     * @throws Exception 解析失败时抛出
     */
    private DocConvertResult convertXlsx(Path file, DocConvertOptions options) throws Exception {
        StringBuilder md = new StringBuilder();
        int sheetCount = 0;
        int totalRows = 0;
        int maxColsSeen = 0;
        boolean truncated = false;

        try (InputStream in = Files.newInputStream(file);
             OPCPackage pkg = OPCPackage.open(in)) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings sst = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();

            while (sheets.hasNext()) {
                try (InputStream sheetIn = sheets.next()) {
                    String sheetName = sheets.getSheetName();
                    sheetCount++;
                    md.append("## ").append(sheetName).append("\n\n");

                    SheetCollector collector = new SheetCollector(options);
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    SAXParser saxParser = factory.newSAXParser();
                    XMLReader parser = saxParser.getXMLReader();
                    XSSFSheetXMLHandler handler =
                            new XSSFSheetXMLHandler(styles, sst, collector, FORMATTER, false);
                    parser.setContentHandler(handler);
                    parser.parse(new InputSource(sheetIn));

                    md.append(collector.toMarkdown());
                    totalRows += collector.getTotalRows();
                    maxColsSeen = Math.max(maxColsSeen, collector.getMaxColsSeen());
                    truncated |= collector.isTruncated();
                }
            }
        }

        return buildResult(md, "xlsx", sheetCount, totalRows, maxColsSeen, truncated, options);
    }

    /**
     * 转换 .xls（HSSF Event API 流式解析）。
     *
     * @param file    .xls 文件
     * @param options 转换选项
     * @return 转换结果
     * @throws Exception 解析失败时抛出
     */
    private DocConvertResult convertLegacyXls(Path file, DocConvertOptions options) throws Exception {
        XlsSheetCollector collector = new XlsSheetCollector(options);
        try (InputStream in = Files.newInputStream(file)) {
            HSSFRequest request = new HSSFRequest();
            request.addListenerForAllRecords(collector);
            new HSSFEventFactory().processEvents(request, in);
        }
        return buildResult(new StringBuilder(collector.toMarkdown()), "xls",
                collector.getSheetCount(), collector.getTotalRows(),
                collector.getMaxColsSeen(), collector.isTruncated(), options);
    }

    /**
     * 统一构建结果（截断提示 + OutputTruncator 兜底）。
     *
     * @param md          Markdown 内容
     * @param format      实际格式（xls/xlsx）
     * @param sheetCount  Sheet 数
     * @param totalRows   总行数
     * @param maxColsSeen 最大列数
     * @param truncated   是否发生行/列截断
     * @param options     转换选项
     * @return 转换结果
     */
    private DocConvertResult buildResult(StringBuilder md, String format, int sheetCount,
                                         int totalRows, int maxColsSeen, boolean truncated,
                                         DocConvertOptions options) {
        if (truncated) {
            md.append("\n> [!WARNING] Excel 数据已截断：共 ")
              .append(totalRows).append(" 行 ").append(maxColsSeen).append(" 列，仅显示前 ")
              .append(options.getMaxRows()).append(" 行 / ")
              .append(options.getMaxCols()).append(" 列\n");
        }
        String markdown = md.toString();
        boolean charTruncated = markdown.length() > options.getMaxOutputChars();
        String finalMd = OutputTruncator.truncate(markdown, options.getMaxOutputChars());

        return DocConvertResult.builder()
                .markdown(finalMd)
                .format(format)
                .pageCount(sheetCount)
                .charCount(markdown.length())
                .truncated(truncated || charTruncated)
                .build();
    }

    /**
     * XSSF SAX 行收集器（{@code SheetContentsHandler} 实现）。
     */
    private static final class SheetCollector implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final DocConvertOptions options;
        private final List<List<String>> rows = new ArrayList<>();
        private List<String> currentRow;
        private int totalRows = 0;
        private int maxColsSeen = 0;
        private boolean truncated = false;

        private SheetCollector(DocConvertOptions options) {
            this.options = options;
        }

        @Override
        public void startRow(int rowNum) {
            totalRows++;
            if (rows.size() < options.getMaxRows()) {
                currentRow = new ArrayList<>();
            } else {
                currentRow = null;
                truncated = true;
            }
        }

        @Override
        public void endRow(int rowNum) {
            if (currentRow != null) {
                rows.add(currentRow);
                currentRow = null;
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (currentRow == null) {
                return;
            }
            int col = columnIndex(cellReference);
            maxColsSeen = Math.max(maxColsSeen, col + 1);
            if (col >= options.getMaxCols()) {
                truncated = true;
                return;
            }
            while (currentRow.size() < col) {
                currentRow.add("");
            }
            currentRow.add(formattedValue == null ? "" : formattedValue);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // 页眉页脚不参与语义降维
        }

        public String toMarkdown() {
            return renderRows(rows);
        }

        public int getTotalRows() {
            return totalRows;
        }

        public int getMaxColsSeen() {
            return maxColsSeen;
        }

        public boolean isTruncated() {
            return truncated;
        }
    }

    /**
     * HSSF Event API 收集器（.xls 流式解析）。
     */
    private static final class XlsSheetCollector implements HSSFListener {

        private final DocConvertOptions options;
        private final List<String> sheetNames = new ArrayList<>();
        private final List<List<List<String>>> sheets = new ArrayList<>();
        private List<String> currentRow;
        private SSTRecord sst;
        private int currentSheetIndex = -1;
        private int totalRows = 0;
        private int maxColsSeen = 0;
        private boolean truncated = false;

        private XlsSheetCollector(DocConvertOptions options) {
            this.options = options;
        }

        @Override
        public void processRecord(Record record) {
            if (record instanceof BoundSheetRecord bsr) {
                sheetNames.add(bsr.getSheetname());
                sheets.add(new ArrayList<>());
            } else if (record instanceof SSTRecord sstRec) {
                sst = sstRec;
            } else if (record instanceof BOFRecord bof) {
                if (bof.getType() == BOFRecord.TYPE_WORKSHEET) {
                    currentSheetIndex++;
                }
            } else if (record instanceof RowRecord) {
                totalRows++;
                if (currentSheetIndex >= 0) {
                    List<List<String>> rows = sheets.get(currentSheetIndex);
                    if (rows.size() < options.getMaxRows()) {
                        currentRow = new ArrayList<>();
                    } else {
                        currentRow = null;
                        truncated = true;
                    }
                }
            } else if (record instanceof LastCellOfRowDummyRecord) {
                if (currentRow != null && currentSheetIndex >= 0) {
                    sheets.get(currentSheetIndex).add(currentRow);
                    currentRow = null;
                }
            } else if (record instanceof LabelSSTRecord lr) {
                addCell(lr.getColumn(), sst == null ? "" : sst.getString(lr.getSSTIndex()).getString());
            } else if (record instanceof NumberRecord nr) {
                addCell(nr.getColumn(), FORMATTER.formatRawCellContents(nr.getValue(), -1, null));
            } else if (record instanceof FormulaRecord fr) {
                addCell(fr.getColumn(), FORMATTER.formatRawCellContents(fr.getValue(), -1, null));
            } else if (record instanceof BoolErrRecord ber) {
                addCell(ber.getColumn(), ber.getBooleanValue() ? "TRUE" : "FALSE");
            } else if (record instanceof BlankRecord br) {
                addCell(br.getColumn(), "");
            }
        }

        private void addCell(int col, String value) {
            if (currentRow == null) {
                return;
            }
            maxColsSeen = Math.max(maxColsSeen, col + 1);
            if (col >= options.getMaxCols()) {
                truncated = true;
                return;
            }
            while (currentRow.size() < col) {
                currentRow.add("");
            }
            currentRow.add(value == null ? "" : value);
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            for (int s = 0; s < sheets.size(); s++) {
                String name = s < sheetNames.size() ? sheetNames.get(s) : ("Sheet" + (s + 1));
                sb.append("## ").append(name).append("\n\n");
                sb.append(renderRows(sheets.get(s)));
            }
            return sb.toString();
        }

        public int getSheetCount() {
            return sheets.size();
        }

        public int getTotalRows() {
            return totalRows;
        }

        public int getMaxColsSeen() {
            return maxColsSeen;
        }

        public boolean isTruncated() {
            return truncated;
        }
    }

    /**
     * 将行集合渲染为 Markdown 表格（首行作为表头 + 分隔行）。
     *
     * @param rows 行集合（每行为单元格字符串列表）
     * @return Markdown 表格文本
     */
    private static String renderRows(List<List<String>> rows) {
        if (rows.isEmpty()) {
            return "(空 Sheet)\n\n";
        }
        int colCount = rows.stream().mapToInt(List::size).max().orElse(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < colCount; c++) {
                String v = c < row.size() ? row.get(c) : "";
                cells.add(v.replace("|", "\\|").replace("\n", " ").replace("\r", " "));
            }
            sb.append("| ").append(String.join(" | ", cells)).append(" |\n");
            if (i == 0) {
                sb.append("| ").append("--- | ".repeat(colCount)).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 解析单元格引用列号（A1 → 0，B3 → 1）。
     *
     * @param cellReference 单元格引用（如 A1、BC12）
     * @return 0 起始的列索引
     */
    private static int columnIndex(String cellReference) {
        int idx = 0;
        for (int i = 0; i < cellReference.length(); i++) {
            char c = cellReference.charAt(i);
            if (Character.isLetter(c)) {
                idx = idx * 26 + (c - 'A' + 1);
            } else {
                break;
            }
        }
        return idx - 1;
    }
}