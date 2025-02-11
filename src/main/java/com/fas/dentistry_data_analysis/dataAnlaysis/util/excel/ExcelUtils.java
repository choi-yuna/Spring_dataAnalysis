package com.fas.dentistry_data_analysis.dataAnlaysis.util.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;

public class ExcelUtils {

    // 엑셀 셀 값을 String으로 변환하는 유틸리티 메소드
    public static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim(); // 공백 제거
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue % 1 == 0) { // 정수인지 확인
                            return String.valueOf((long) numericValue);
                        } else {
                            return String.valueOf(numericValue);
                        }
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    // 수식을 무시하고 캐시된 값 사용
                    switch (cell.getCachedFormulaResultType()) {
                        case STRING:
                            return cell.getRichStringCellValue().getString().trim(); // 공백 제거
                        case NUMERIC:
                            double formulaValue = cell.getNumericCellValue();
                            if (formulaValue % 1 == 0) { // 정수인지 확인
                                return String.valueOf((long) formulaValue);
                            } else {
                                return String.valueOf(formulaValue);
                            }
                        case BOOLEAN:
                            return String.valueOf(cell.getBooleanCellValue());
                        default:
                            return "";
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            // 오류 발생 시 빈 문자열 반환 및 로그 출력
            System.err.println("Error processing cell: " + cell.getAddress() + ", " + e.getMessage());
            return "";
        }
    }
}
