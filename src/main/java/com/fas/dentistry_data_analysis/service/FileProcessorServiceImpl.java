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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


@Slf4j
@Service
public class FileProcessorServiceImpl implements FileProcessor{

    @Override
    public List<Map<String, Map<String, String>>> processFile(File file, String diseaseClass, int institutionId) throws IOException {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".xlsx")) {
            return processExcelFile(file, diseaseClass, institutionId);
        } else {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
        }
    }

    public List<String> loadPassIdsFromJson(String filePath) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            // JSON 파일에서 ID 리스트 불러오기
            return objectMapper.readValue(new File(filePath), new TypeReference<List<String>>() {});
        } catch (IOException e) {
            log.error("Pass된 ID를 JSON에서 읽는 중 오류가 발생했습니다: {}", filePath, e);
            return Collections.emptyList(); // 읽기에 실패한 경우 빈 리스트 반환
        }
    }

    @Override
    public List<Map<String, Map<String, String>>> processExcelFile(File excelFile, String diseaseClass, int institutionId) throws IOException {

        List<String> passIds = loadPassIdsFromJson("C:/app/id/pass_ids.json");
        List<Map<String, Map<String, String>>> dataList = new ArrayList<>();
        List<Future<Map<String, Map<String, String>>>> futureResults = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(4); // 스레드풀 생성

        try (InputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            String fileName = excelFile.getName();
            int numberOfSheets = workbook.getNumberOfSheets();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                // 시트 이름 필터링
                if (fileName.contains("두개안면") && !(sheet.getSheetName().contains("CRF") && sheet.getSheetName().contains("두개안면기형"))) {
                    continue;
                } else if (fileName.contains("치주질환") && !(sheet.getSheetName().contains("CRF") && sheet.getSheetName().contains("치주질환"))) {
                    continue;
                } else if (fileName.contains("구강암") && !(sheet.getSheetName().contains("CRF") && sheet.getSheetName().contains("구강암"))) {
                    continue;
                } else if (fileName.contains("골수염") && !(sheet.getSheetName().contains("CRF") && sheet.getSheetName().contains("골수염"))) {
                    continue;
                } else if (!(sheet.getSheetName().contains("CRF"))) {
                    continue; // "CRF"가 없는 시트 건너뜀
                }

                String sheetName = sheet.getSheetName().trim();
                Map<String, List<String>> headerConfig = SheetHeaderMapping.getHeadersForSheet(sheetName);
                if (headerConfig != null) { // 매핑된 헤더가 있는 경우만 처리
                    List<String> requiredHeaders = headerConfig.get("required");
                    List<String> optionalHeaders = headerConfig.get("optional");

                    Row headerRow = sheet.getRow(3); // 4번째 행을 헤더로 설정
                    if (headerRow == null) {
                        throw new RuntimeException("헤더 행이 존재하지 않습니다. 파일을 확인해주세요.");
                    }

                    // 헤더 인덱스 매핑
                    Map<String, Integer> headerIndexMap = new HashMap<>();
                    for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
                        Cell cell = headerRow.getCell(cellIndex);
                        if (cell != null) {
                            String headerName = cell.getStringCellValue().trim();
                            if (requiredHeaders.contains(headerName) || optionalHeaders.contains(headerName)) {
                                headerIndexMap.put(headerName, cellIndex);
                            }
                        }
                    }

                    Integer diseaseClassIndex = headerIndexMap.get("DISEASE_CLASS");
                    Integer institutionIdIndex = headerIndexMap.get("INSTITUTION_ID");
                    Integer imageIdIndex = headerIndexMap.get("IMAGE_ID");

                    if (diseaseClassIndex != null && institutionIdIndex != null) {
                        for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) { // 9번째 행부터 데이터 읽기
                            Row row = sheet.getRow(rowIndex);
                            if (row != null) {
                                Future<Map<String, Map<String, String>>> future = executor.submit(() -> {
                                    Map<String, String> requiredData = new LinkedHashMap<>();
                                    Map<String, String> optionalData = new LinkedHashMap<>();

                                    // Pass된 ID인지 확인
                                    String imageIdValue = ExcelUtils.getCellValueAsString(row.getCell(imageIdIndex));
                                    // 질환 클래스와 기관 ID 값 가져오기
                                    String diseaseClassValue = ExcelUtils.getCellValueAsString(row.getCell(diseaseClassIndex));
                                    String institutionIdValueStr = ExcelUtils.getCellValueAsString(row.getCell(institutionIdIndex));

                                    if (!passIds.contains(imageIdValue)) {
                                        return Collections.emptyMap();
                                    }


                                    // 질환 클래스 또는 기관 ID가 비어 있으면 제외
                                    if (diseaseClassValue == null || diseaseClassValue.isEmpty() ||
                                            institutionIdValueStr == null || institutionIdValueStr.isEmpty()) {
                                        return Collections.emptyMap(); // 빠르게 제외
                                    }

                                    try {
                                        int institutionIdValue = Integer.parseInt(institutionIdValueStr);

                                        // 필터 조건: 질환 클래스와 기관 ID 검사
                                        if (!((diseaseClass.equals("0") || diseaseClass.equals(diseaseClassValue)) &&
                                                (institutionId == 0 || institutionId == institutionIdValue))) {
                                            return Collections.emptyMap(); // 조건에 맞지 않으면 제외
                                        }
                                    } catch (NumberFormatException e) {
                                        System.err.println("숫자로 변환할 수 없는 institutionId 값: " + institutionIdValueStr);
                                        return Collections.emptyMap(); // 숫자 변환 실패 시 제외
                                    }

                                    // 필수 항목 처리
                                    for (String header : requiredHeaders) {
                                        Integer cellIndex = headerIndexMap.get(header);
                                        if (cellIndex != null) {
                                            Cell cell = row.getCell(cellIndex);
                                            String cellValue = (cell != null) ? ExcelUtils.getCellValueAsString(cell) : "";
                                            requiredData.put(header, cellValue);
                                        }
                                    }

                                    // 선택 항목 처리
                                    for (String header : optionalHeaders) {
                                        Integer cellIndex = headerIndexMap.get(header);
                                        if (cellIndex != null) {
                                            Cell cell = row.getCell(cellIndex);
                                            String cellValue = (cell != null) ? ExcelUtils.getCellValueAsString(cell) : "";
                                            optionalData.put(header, cellValue);
                                        }
                                    }

                                    // 필수 및 선택 항목이 모두 비어 있으면 제외
                                    if (requiredData.isEmpty() && optionalData.isEmpty()) {
                                        return Collections.emptyMap();
                                    }

                                    // 결과 데이터 생성
                                    Map<String, Map<String, String>> rowData = new HashMap<>();
                                    rowData.put("required", requiredData);
                                    rowData.put("optional", optionalData);
                                    return rowData;
                                });

                                futureResults.add(future);
                            }
                        }
                    }
                }
            }

            // 병렬 처리된 결과를 수집
            for (Future<Map<String, Map<String, String>>> future : futureResults) {
                try {
                    Map<String, Map<String, String>> result = future.get();
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
