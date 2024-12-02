package com.fas.dentistry_data_analysis.service.dashBoard;

import com.fas.dentistry_data_analysis.util.ExcelUtils;
import com.fas.dentistry_data_analysis.util.ValueMapping;
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

    // 엑셀 파일 처리
    public List<Map<String, Object>> processExcelFile(InputStream inputStream) throws IOException, ExecutionException, InterruptedException {
        log.info("Processing Excel file");

        List<Map<String, Object>> filteredData = new ArrayList<>();

        // Excel 파일을 비동기적으로 읽고 처리
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            int numberOfSheets = workbook.getNumberOfSheets();
            log.info("Excel file contains {} sheets", numberOfSheets);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                if (!sheet.getSheetName().contains("CRF")) {
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

    private void processSheet(Sheet sheet, List<Map<String, Object>> filteredData) {
        Row headerRow = sheet.getRow(3); // Header row is at row index 3
        if (headerRow == null) {
            return;
        }

        Map<String, Integer> headerIndexMap = new HashMap<>();
        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            Cell cell = headerRow.getCell(cellIndex);
            if (cell != null) {
                String headerName = cell.getStringCellValue().trim();
                headerIndexMap.put(headerName, cellIndex);
            }
        }

        // 데이터를 읽어옵니다.
        for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                Map<String, Object> rowData = new LinkedHashMap<>();

                Integer imageIdIndex = headerIndexMap.get("IMAGE_ID");
                Integer diseaseClassIndex = headerIndexMap.get("DISEASE_CLASS");
                Integer institutionIdIndex = headerIndexMap.get("INSTITUTION_ID");

                // IMAGE_ID 추출
                if (imageIdIndex != null) {
                    Cell imageIdCell = row.getCell(imageIdIndex);
                    String imageIdValue = (imageIdCell != null) ? ExcelUtils.getCellValueAsString(imageIdCell) : "";
                    if (imageIdValue == null || imageIdValue.isEmpty()) {continue;}
                    rowData.put("IMAGE_ID", imageIdValue);
                }

                // DISEASE_CLASS 추출
                if (diseaseClassIndex != null) {
                    Cell diseaseClassCell = row.getCell(diseaseClassIndex);
                    String diseaseClassValue = (diseaseClassCell != null) ? ExcelUtils.getCellValueAsString(diseaseClassCell) : "";
                    if (diseaseClassValue == null || diseaseClassValue.isEmpty()) {continue;}
                    String mappedDiseaseClass = ValueMapping.getDiseaseClass(diseaseClassValue);
                    rowData.put("DISEASE_CLASS", mappedDiseaseClass);
                }

                // INSTITUTION_ID 추출
                if (institutionIdIndex != null) {
                    Cell institutionIdCell = row.getCell(institutionIdIndex);
                    String institutionIdValue = (institutionIdCell != null) ? ExcelUtils.getCellValueAsString(institutionIdCell) : "";
                    if (institutionIdValue == null || institutionIdValue.isEmpty()) {continue;}
                    String mappedInstitutionId = ValueMapping.getInstitutionDescription(institutionIdValue);
                    rowData.put("INSTITUTION_ID", mappedInstitutionId);
                }

                if (!rowData.isEmpty()) {
                    synchronized (filteredData) {  // 여러 스레드에서 동시에 추가되지 않도록 동기화
                        filteredData.add(rowData);
                    }
                }
            }
        }
    }
}
