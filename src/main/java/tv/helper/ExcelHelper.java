package tv.helper;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Date;

public class ExcelHelper {
    public static <T extends Collection<Object>> byte[] generateExcelWorkbook(
        @NotNull String sheetName,
        @NotNull Collection<T> items,
        @NotNull String... columns
    ) {
        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet(sheetName);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerFont.setColor(IndexedColors.RED.getIndex());

        CellStyle headerCellStyle = workbook.createCellStyle();
        headerCellStyle.setFont(headerFont);

        Row headerRow = sheet.createRow(0);

        CellStyle dateCellStyle = workbook.createCellStyle();
        dateCellStyle.setDataFormat((short) 0xf); // 0xf, "d-mmm-yy", see BuiltinFormats

        CellStyle integerCellStyle = workbook.createCellStyle();
        integerCellStyle.setDataFormat((short) 1);

        CellStyle fractionalCellStyle = workbook.createCellStyle();
        fractionalCellStyle.setDataFormat((short) 2);

        CellStyle textCellStyle = workbook.createCellStyle();
        textCellStyle.setDataFormat((short) 0x31);

        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerCellStyle);
        }

        int rowNum = 1;
        for (T rowItems : items) {
            Row row = sheet.createRow(rowNum++);
            int columnNum = 0;
            for (Object cellItem : rowItems) {
                Cell cell = row.createCell(columnNum++);
                if (cellItem instanceof Number) {
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
        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
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
}
