package com.fas.dentistry_data_analysis.dashboard.Service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class ExcelService {

    /**
     * 엑셀 파일을 읽고 특정 질환 클래스(diseaseClass)에 해당하는 데이터를 필터링하여 반환
     *
     * @param inputStream  엑셀 파일의 입력 스트림
     * @param diseaseClass 검색할 질환 클래스
     * @return 필터링된 데이터 리스트 (List<Map<String, Object>>)
     * @throws IOException       파일 읽기 오류 발생 시
     * @throws ExecutionException 비동기 작업 실행 오류 발생 시
     * @throws InterruptedException 비동기 작업이 인터럽트될 경우
     */

    public List<Map<String, Object>> processExcelFile(InputStream inputStream, String diseaseClass) throws IOException, ExecutionException, InterruptedException {
        log.info("Processing Excel file");

        List<Map<String, Object>> filteredData = new ArrayList<>();

        // Excel 파일을 비동기적으로 읽고 처리
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            int numberOfSheets = workbook.getNumberOfSheets();
            log.info("Excel file contains {} sheets", numberOfSheets);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                if (!(sheet.getSheetName().contains("CRF") && sheet.getSheetName().contains(diseaseClass))) {
                    continue;
                }

                // 각 시트를 비동기적으로 처리
                futures.add(CompletableFuture.runAsync(() -> processSheet(sheet, filteredData)));
            }

            // 모든 시트의 처리가 완료될 때까지 기다림
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        return filteredData;
    }


    /**
     * 주어진 시트를 분석하여 필터링된 데이터를 리스트에 추가
     *
     * @param sheet        처리할 엑셀 시트
     * @param filteredData 필터링된 데이터를 저장할 리스트
     */
    private void processSheet(Sheet sheet, List<Map<String, Object>> filteredData) {
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator(); // 수식 평가기 생성

        log.debug("Processing sheet: {}", sheet.getSheetName());

        Row headerRow = sheet.getRow(3); // Header row is at row index 3
        if (headerRow == null) {
            log.warn("Header row is missing in sheet: {}", sheet.getSheetName());
            return;
        }

        Map<String, Integer> headerIndexMap = new HashMap<>();
        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            Cell cell = headerRow.getCell(cellIndex);
            if (cell != null) {
                String headerName = cell.getStringCellValue().trim();
                headerIndexMap.put(headerName, cellIndex);
                log.debug("Header found: {} at column {}", headerName, cellIndex);
            }
        }

        // 데이터를 읽어옵니다.
        for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                Map<String, Object> rowData = new LinkedHashMap<>();

                Integer imageIdIndex = headerIndexMap.get("IMAGE_ID");
                //질환 인덱스
                //Integer diseaseClassIndex = headerIndexMap.get("DISEASE_CLASS");
                //기관 인덱스
                //Integer institutionIdIndex = headerIndexMap.get("INSTITUTION_ID");

                // IMAGE_ID 추출
                if (imageIdIndex != null) {
                    Cell imageIdCell = row.getCell(imageIdIndex);
                    String imageIdValue = (imageIdCell != null) ? getCellValueAsString(imageIdCell) : "";
                    if (imageIdValue == null || imageIdValue.isEmpty()) {
                        continue;
                    }
                    rowData.put("IMAGE_ID", imageIdValue);
                }
                if (!rowData.isEmpty()) {
                    synchronized (filteredData) {  // 여러 스레드에서 동시에 추가되지 않도록 동기화
                        filteredData.add(rowData);
                    }
                }
            }
        }
    }

    /**
     * 엑셀 셀의 값을 문자열로 변환하여 반환
     *
     * @param cell 변환할 엑셀 셀
     * @return 문자열로 변환된 셀 값
     */
    private String getCellValueAsString(Cell cell) {
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString(); // 날짜인 경우 처리
                    }
                    double numericValue = cell.getNumericCellValue();
                    // 정수인지 확인 후 변환
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((long) numericValue); // 정수로 반환
                    }
                    return String.valueOf(numericValue); // 소수점 있는 경우 그대로 반환
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    // 수식을 무시하고 캐싱된 결과값 사용
                    switch (cell.getCachedFormulaResultType()) {
                        case STRING:
                            return cell.getRichStringCellValue().getString();
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                return cell.getDateCellValue().toString();
                            }
                            return String.valueOf(cell.getNumericCellValue());
                        case BOOLEAN:
                            return String.valueOf(cell.getBooleanCellValue());
                        default:
                            return ""; // 캐싱된 결과값이 없을 경우 빈 문자열 반환
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            log.error("Error processing cell at {}: {}", cell.getAddress(), e.getMessage());
            return "";
        }
    }


}