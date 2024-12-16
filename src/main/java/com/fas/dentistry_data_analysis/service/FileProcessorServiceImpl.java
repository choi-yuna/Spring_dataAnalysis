package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.util.SheetHeaderMapping;
import com.fas.dentistry_data_analysis.util.ExcelUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Service
public class FileProcessorServiceImpl implements FileProcessor{

    @Override
    public List<Map<String, String>> processFile(File file, String diseaseClass, int institutionId) throws IOException {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".xlsx")) {
            return processExcelFile(file, diseaseClass, institutionId);
        } else {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
        }
    }

    @Override
    // 엑셀 파일 처리 및 필터링 메소드
    public List<Map<String, String>> processExcelFile(File excelFile, String diseaseClass, int institutionId) throws IOException {
        List<Map<String, String>> dataList = new ArrayList<>();
        List<Future<Map<String, String>>> futureResults = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(4); // 스레드풀 생성

        try (InputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            String fileName = excelFile.getName();
            int numberOfSheets = workbook.getNumberOfSheets();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (fileName.contains("두개안면")) {
                    if (!((sheet.getSheetName().contains("CRF")) && (sheet.getSheetName().contains("두개안면기형")))) {
                        continue; // "두개안면 CRF"가 아닌 경우 건너뜀
                    }
                }
                else if (fileName.contains("치주질환")) {
                    if (!((sheet.getSheetName().contains("CRF")) && (sheet.getSheetName().contains("치주질환")))) {
                        continue; // "두개안면 CRF"가 아닌 경우 건너뜀
                    }

                }
                else if (fileName.contains("구강암")) {
                    if (!((sheet.getSheetName().contains("CRF")) && (sheet.getSheetName().contains("구강암")))) {
                        continue; // "두개안면 CRF"가 아닌 경우 건너뜀
                    }

                }
                else if (fileName.contains("골수염")) {
                    if (!((sheet.getSheetName().contains("CRF")) && (sheet.getSheetName().contains("골수염")))) {
                        continue; // "두개안면 CRF"가 아닌 경우 건너뜀
                    }
                }
                else {
                    // 일반 파일인 경우 "CRF"가 포함된 시트만 처리
                    if (!(sheet.getSheetName().contains("CRF"))){
                        continue;
                    }
                }
                    String sheetName = sheet.getSheetName().trim();
                List<String> expectedHeaders = SheetHeaderMapping.getHeadersForSheet(sheetName);
                if (expectedHeaders != null) {  // 매핑된 헤더가 있는 경우에만 처리
                    Row headerRow = sheet.getRow(3); // 4번째 행을 헤더로 설정
                    if (headerRow == null) {
                        throw new RuntimeException("헤더 행이 존재하지 않습니다. 파일을 확인해주세요.");
                    }

                    // 엑셀 파일의 헤더를 읽어옴
                    Map<String, Integer> headerIndexMap = new HashMap<>();
                    for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
                        Cell cell = headerRow.getCell(cellIndex);
                        if (cell != null) {
                            String headerName = cell.getStringCellValue().trim();
                            if (expectedHeaders.contains(headerName)) {
                                headerIndexMap.put(headerName, cellIndex);
                            }
                        }
                    }

                    Integer diseaseClassIndex = headerIndexMap.get("DISEASE_CLASS");
                    Integer institutionIdIndex = headerIndexMap.get("INSTITUTION_ID");

                    if (diseaseClassIndex != null && institutionIdIndex != null) {
                        for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {  // 9번째 행부터 데이터 읽기

                            Row row = sheet.getRow(rowIndex);
                            if (row != null) {
                                // 각 행을 병렬로 처리하도록 스레드풀에 제출
                                Future<Map<String, String>> future = executor.submit(() -> {
                                    Map<String, String> rowData = new LinkedHashMap<>();
                                    String diseaseClassValue = ExcelUtils.getCellValueAsString(row.getCell(diseaseClassIndex));
                                    String institutionIdValueStr = ExcelUtils.getCellValueAsString(row.getCell(institutionIdIndex));
                                    if (!institutionIdValueStr.isEmpty()) {
                                        try {
                                            int institutionIdValue = Integer.parseInt(institutionIdValueStr);
                                            // 빈 값도 허용하도록 수정: 빈 값인 경우 조건을 패스
                                            if ((diseaseClass.equals("0") || diseaseClass.equals("") || diseaseClass.equals(diseaseClassValue)) &&
                                                    (institutionId == 0 || institutionIdValueStr.isEmpty() || institutionId == institutionIdValue)) {
                                                for (String header : expectedHeaders) {
                                                    Integer cellIndex = headerIndexMap.get(header);
                                                    if (cellIndex != null) {
                                                        Cell cell = row.getCell(cellIndex);
                                                        String cellValue = (cell != null) ? ExcelUtils.getCellValueAsString(cell) : "";
                                                        rowData.put(header, cellValue);
                                                    }
                                                }
                                            }
                                        } catch (NumberFormatException e) {
                                            System.err.println("숫자로 변환할 수 없는 institutionId 값: " + institutionIdValueStr);
                                        }
                                    }
                                    return rowData;
                                });
                                futureResults.add(future);
                            }
                        }
                    }
                }
            }

            // 각 병렬 처리된 결과를 수집
            for (Future<Map<String, String>> future : futureResults) {
                try {
                    Map<String, String> result = future.get();
                    if (!result.isEmpty()) {
                        dataList.add(result);
                    }
                } catch (Exception e) {
                    System.err.println("병렬 처리 중 오류 발생: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("엑셀 파일 처리 중 오류 발생: " + e.getMessage());
            throw e;
        } finally {
            executor.shutdown();
        }

        return dataList;
    }

    // 셀 데이터를 String 변환하는 메소드
    }
