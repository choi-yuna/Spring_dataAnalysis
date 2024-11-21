package com.fas.dentistry_data_analysis.service.dashBoard;

import com.fas.dentistry_data_analysis.util.ExcelUtils;
import com.fas.dentistry_data_analysis.util.ValueMapping;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class ExcelService {

    // 엑셀 파일 처리
    public List<Map<String, Object>> processExcelFile(InputStream inputStream) throws IOException {
        log.info("Processing Excel file");

        List<Map<String, Object>> filteredData = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            int numberOfSheets = workbook.getNumberOfSheets();
            log.info("Excel file contains {} sheets", numberOfSheets);

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                if (!sheet.getSheetName().contains("CRF")) {
                    continue;
                }

                Row headerRow = sheet.getRow(3); // Header row is at row index 3
                if (headerRow == null) {
                    continue;
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
                            rowData.put("IMAGE_ID", imageIdValue);
                        }

                        // DISEASE_CLASS 추출
                        if (diseaseClassIndex != null) {
                            Cell diseaseClassCell = row.getCell(diseaseClassIndex);
                            String diseaseClassValue = (diseaseClassCell != null) ? ExcelUtils.getCellValueAsString(diseaseClassCell) : "";
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
                            filteredData.add(rowData);
                        }
                    }
                }
            }
        }
        log.info("Processed {} rows from Excel file", filteredData.size());
        return filteredData;
    }
}
