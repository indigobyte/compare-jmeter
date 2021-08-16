package tv.helper;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class ExcelHelper {
    @NotNull
    private static XSSFRow getOrCreateRow(
            @NotNull XSSFSheet sheet,
            int rowNum
    ) {
        var row = sheet.getRow(rowNum);
        if (row != null) {
            return row;
        }
        return sheet.createRow(rowNum);
    }

    public static <T extends Collection<Object>> byte[] generateExcelWorkbook(
            @NotNull String sheetName,
            @NotNull Collection<T> items,
            @NotNull List<List<HeaderCellParams>> headerRowParams
    ) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet(sheetName);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        //headerFont.setFontHeightInPoints((short) 12);

        var linkFont = workbook.createFont();
        linkFont.setColor(IndexedColors.BLUE.getIndex());
        linkFont.setUnderline(FontUnderline.SINGLE);

        CellStyle headerCellStyle = workbook.createCellStyle();
        headerCellStyle.setFont(headerFont);
        headerCellStyle.setAlignment(HorizontalAlignment.CENTER);
        headerCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle dateCellStyle = workbook.createCellStyle();
        dateCellStyle.setDataFormat((short) 0xf); // 0xf, "d-mmm-yy", see BuiltinFormats

        CellStyle integerCellStyle = workbook.createCellStyle();
        integerCellStyle.setDataFormat((short) 1); // 1, "0"
        integerCellStyle.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle fractionalCellStyle = workbook.createCellStyle();
        fractionalCellStyle.setDataFormat((short) 2); // 2, "0.00"
        fractionalCellStyle.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle textCellStyle = workbook.createCellStyle();
        textCellStyle.setDataFormat((short) 0x31); // 0x31, "@" - This is text format.
        textCellStyle.setAlignment(HorizontalAlignment.JUSTIFY);

        int columnCount = 0;

        int headerRowCount = headerRowParams.size();
        if (headerRowCount > 0) {
            int headerColumnCount = columnCount = headerRowParams.get(0).size();
            for (int i = 0; i < headerRowCount; ++i) {
                var row = getOrCreateRow(sheet, i);
                for (int j = 0; j < headerColumnCount; ++j) {
                    final Cell cell = row.createCell(j);
                    cell.setCellStyle(headerCellStyle);
                    final HeaderCellParams cellParams = headerRowParams.get(i).get(j);
                    if (cellParams != null) {
                        cell.setCellValue(cellParams.text());
                    }
                }
            }
            for (int i = 0; i < headerRowCount; ++i) {
                for (int j = 0; j < headerColumnCount; ++j) {
                    final HeaderCellParams cellParams = headerRowParams.get(i).get(j);
                    if (cellParams != null && (
                            cellParams.rowSpan() != 1 ||
                                    cellParams.colSpan() != 1
                    )) {
                        sheet.addMergedRegion(new CellRangeAddress(
                                i,
                                i + cellParams.rowSpan() - 1,
                                j,
                                j + cellParams.colSpan() - 1
                        ));
                    }
                }
            }
        }
        int rowNum = headerRowCount;
        for (T rowItems : items) {
            var row = getOrCreateRow(sheet, rowNum++);
            int columnNum = 0;
            for (Object cellItem : rowItems) {
                var cell = row.createCell(columnNum++);
                columnCount = Math.max(columnCount, columnNum);

                setCellData(
                        workbook,
                        linkFont,
                        dateCellStyle,
                        integerCellStyle,
                        fractionalCellStyle,
                        textCellStyle,
                        rowNum,
                        columnNum,
                        cellItem,
                        cell
                );
            }
        }
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i, true);
        }
        try {
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            workbook.write(bytesOut);
            bytesOut.close();
            workbook.close();
            return bytesOut.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setCellData(
            @NotNull Workbook workbook,
            @NotNull XSSFFont linkFont,
            @NotNull CellStyle dateCellStyle,
            @NotNull CellStyle integerCellStyle,
            @NotNull CellStyle fractionalCellStyle,
            @NotNull CellStyle textCellStyle,
            int rowNum,
            int columnNum,
            @Nullable Object cellItem,
            @NotNull XSSFCell cell
    ) {
        if (cellItem instanceof CellWithLink cellWithLink) {
            setCellData(
                    workbook,
                    linkFont,
                    dateCellStyle,
                    integerCellStyle,
                    fractionalCellStyle,
                    textCellStyle,
                    rowNum,
                    columnNum,
                    cellWithLink.value(),
                    cell
            );
            Hyperlink link = workbook
                    .getCreationHelper()
                    .createHyperlink(HyperlinkType.URL);
            link.setAddress(cellWithLink.url());
            cell.setHyperlink(link);
            XSSFCellStyle cellStyle = (XSSFCellStyle)cell.getCellStyle().clone();
            cellStyle.setFont(linkFont);
            cell.setCellStyle(cellStyle);
        } else if (cellItem instanceof Number) {
            if (cellItem instanceof Integer ||
                    cellItem instanceof Byte ||
                    cellItem instanceof Short ||
                    cellItem instanceof Long ||
                    cellItem instanceof BigInteger
            ) {
                cell.setCellStyle(integerCellStyle);
            } else {
                cell.setCellStyle(fractionalCellStyle);
            }
            cell.setCellValue(((Number) cellItem).doubleValue());
        } else if (cellItem instanceof Date) {
            cell.setCellStyle(dateCellStyle);
            cell.setCellValue((Date) cellItem);
        } else if (cellItem instanceof String) {
            cell.setCellStyle(textCellStyle);
            cell.setCellValue((String) cellItem);
        } else if (cellItem instanceof Boolean) {
            cell.setCellValue((Boolean) cellItem);
        } else if (cellItem == null) {
            cell.setCellValue((String) null);
        } else {
            throw new IllegalArgumentException("Unsupported cell value " + cellItem + " for cell " + rowNum + ", " + columnNum);
        }
    }
}
