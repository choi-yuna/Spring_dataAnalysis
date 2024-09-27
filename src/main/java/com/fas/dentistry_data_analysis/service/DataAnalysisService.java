package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.config.SheetHeaderMapping;
import com.fas.dentistry_data_analysis.util.HeaderMapping;
import com.fas.dentistry_data_analysis.util.ValueMapping;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

@Service
public class DataAnalysisService {


    private final FileStorageService fileStorageService;
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Autowired
    public DataAnalysisService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    // 다중 파일 ID를 기반으로 데이터 분석을 수행하는 메소드
    public List<Map<String, String>> analyzeData(String[] fileIds, String diseaseClass, int institutionId) throws IOException, InterruptedException, ExecutionException {
        if (fileIds == null || fileIds.length == 0) {
            throw new IllegalArgumentException("파일 ID 목록이 비어있거나 null입니다.");
        }

        List<Future<List<Map<String, String>>>> futureResults = new ArrayList<>();
        for (String fileId : fileIds) {
            Path filePath = fileStorageService.getFilePath(fileId);
            if (filePath == null) {
                throw new IOException("파일을 찾을 수 없습니다. 파일 ID: " + fileId);
            }

            // 각 파일을 병렬 처리하도록 스레드풀에 제출
            Future<List<Map<String, String>>> future = executor.submit(() -> processFile(new File(filePath.toString()), diseaseClass, institutionId));
            futureResults.add(future);
        }

        List<Map<String, String>> combinedData = new ArrayList<>();
        for (Future<List<Map<String, String>>> future : futureResults) {
            combinedData.addAll(future.get()); // 결과를 합침
        }

        return combinedData;
    }


    // 파일 확장자를 기반으로 엑셀 파일 처리
    public List<Map<String, String>> processFile(File file, String diseaseClass, int institutionId) throws IOException {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".xlsx")) {
            return processExcelFile(file, diseaseClass, institutionId);
        } else {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
        }
    }

    // 엑셀 파일 처리 및 필터링 메소드
    public List<Map<String, String>> processExcelFile(File excelFile, String diseaseClass, int institutionId) throws IOException {
        List<Map<String, String>> dataList = new ArrayList<>();
        List<Future<Map<String, String>>> futureResults = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(4); // 스레드풀 생성

        try (InputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            int numberOfSheets = workbook.getNumberOfSheets();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);
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
                                    String diseaseClassValue = getCellValueAsString(row.getCell(diseaseClassIndex));
                                    String institutionIdValueStr = getCellValueAsString(row.getCell(institutionIdIndex));
                                    if (!institutionIdValueStr.isEmpty()) {
                                        try {
                                            int institutionIdValue = Integer.parseInt(institutionIdValueStr);
                                            // 필터링 조건 수정: 질환 또는 기관이 "0"이면 필터링하지 않음
                                            if ((diseaseClass.equals("0") || diseaseClassValue.equals(diseaseClass)) &&
                                                    (institutionId == 0 || institutionIdValue == institutionId)) {
                                                for (String header : expectedHeaders) {
                                                    Integer cellIndex = headerIndexMap.get(header);
                                                    if (cellIndex != null) {
                                                        Cell cell = row.getCell(cellIndex);
                                                        String cellValue = (cell != null) ? getCellValueAsString(cell) : "";
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


    // 동적 필터링을 위한 메소드 (기존 유지 + 지역별 매핑 및 카운트 추가)
    public List<Map<String, Object>> analyzeDataWithFilters(String[] fileIds, Map<String, String> filterConditions, List<String> headers) throws IOException {
        if (fileIds == null || fileIds.length == 0) {
            throw new IllegalArgumentException("파일 ID 목록이 비어있거나 null입니다.");
        }

        List<Map<String, Object>> responseList = new ArrayList<>();
        ExecutorService executor = Executors.newCachedThreadPool();

        try {
            // 비동기 파일 처리
            List<Future<List<Map<String, String>>>> futures = new ArrayList<>();
            for (String fileId : fileIds) {
                futures.add(executor.submit(() -> {
                    Path filePath = fileStorageService.getFilePath(fileId);
                    if (filePath == null) {
                        throw new IOException("파일을 찾을 수 없습니다. 파일 ID: " + fileId);
                    }
                    Map<String, String> fileFilterConditions = new HashMap<>(filterConditions);
                    if ("All".equals(fileFilterConditions.get("DISEASE_CLASS"))) {
                        fileFilterConditions.remove("DISEASE_CLASS");
                    }
                    return processFileWithFilters(new File(filePath.toString()), fileFilterConditions, headers);
                }));
            }

            // 결과 처리
            for (String header : headers) {
                if ("Tooth".equals(header)) {
                    // Tooth 필드 요약 처리
                    Map<String, Object> result = new HashMap<>();
                    result.put("headers", Arrays.asList("치아 상태", "개수"));
                    result.put("id", "Tooth");
                    result.put("title", "치아 상태 요약");

                    // 치아 상태 빈도수 계산
                    int implantCount = 0;
                    int prosthesisCount = 0;
                    int normalCount = 0;
                    int bridgeCount = 0;
                    int otherCount = 0;

                    for (Future<List<Map<String, String>>> future : futures) {
                        List<Map<String, String>> fileData = future.get();

                        for (Map<String, String> rowData : fileData) {
                            for (Map.Entry<String, String> entry : rowData.entrySet()) {
                                String key = entry.getKey();
                                String value = entry.getValue().trim();

                                if (key.startsWith("Tooth_")) {
                                    switch (value) {
                                        case "1":  // 정상
                                            normalCount++;
                                            break;
                                        case "2":  // 보철
                                            prosthesisCount++;
                                            break;
                                        case "3":  // 임플란트
                                            implantCount++;
                                            break;
                                        case "4":  // 브릿지
                                            bridgeCount++;
                                            break;
                                        case "5": case "6":  // 기타
                                            otherCount++;
                                            break;
                                    }
                                }
                            }
                        }
                    }

                    // 결과 저장
                    List<Map<String, Object>> rows = new ArrayList<>();
                    rows.add(Map.of("value", "정상", "count", normalCount));
                    rows.add(Map.of("value", "보철", "count", prosthesisCount));
                    rows.add(Map.of("value", "임플란트", "count", implantCount));
                    rows.add(Map.of("value", "브릿지", "count", bridgeCount));
                    rows.add(Map.of("value", "기타", "count", otherCount));

                    result.put("rows", rows);
                    responseList.add(result);

                } else if ("P_RES_AREA".equals(header)) {
                    // 지역별 필드 요약 처리
                    Map<String, Object> result = new HashMap<>();
                    result.put("headers", HeaderMapping.determineHeadersBasedOnFilters(Collections.singletonList(header))); // 헤더 동적 처리
                    result.put("id", "P_RES_AREA");
                    result.put("title", HeaderMapping.determineTitleBasedOnHeaders(Collections.singletonList(header))); // 타이틀 동적 처리

                    Map<String, Integer> regionCounts = new HashMap<>();

                    for (Future<List<Map<String, String>>> future : futures) {
                        List<Map<String, String>> fileData = future.get();

                        for (Map<String, String> rowData : fileData) {
                            String region = rowData.getOrDefault("P_RES_AREA", "").trim();
                            if (!region.isEmpty()) {  // null 또는 빈 값 처리
                                String mappedRegion = mapRegionName(region);  // 지역명 매핑
                                regionCounts.put(mappedRegion, regionCounts.getOrDefault(mappedRegion, 0) + 1);
                            }
                        }
                    }

                    // 지역별 카운트를 rows 리스트로 변환
                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (Map.Entry<String, Integer> entry : regionCounts.entrySet()) {
                        rows.add(Map.of("value", entry.getKey(), "count", entry.getValue()));
                    }

                    result.put("rows", rows);
                    responseList.add(result);

                } else {
                    // 일반적인 헤더 처리
                    Map<String, Object> result = new HashMap<>();
                    String title = HeaderMapping.determineTitleBasedOnHeaders(Collections.singletonList(header));
                    List<String> dynamicHeaders = HeaderMapping.determineHeadersBasedOnFilters(Collections.singletonList(header));
                    result.put("id", header);
                    result.put("title", title);
                    result.put("headers", dynamicHeaders);

                    // 빈도수 계산
                    Map<String, Integer> valueCounts = new HashMap<>();

                    for (Future<List<Map<String, String>>> future : futures) {
                        List<Map<String, String>> fileData = future.get();

                        for (Map<String, String> rowData : fileData) {
                            String value = rowData.getOrDefault(header, "").trim();
                            if (!value.isEmpty()) {
                                String mappedValue = ValueMapping.headerMappingFunctions
                                        .getOrDefault(header, Function.identity())
                                        .apply(value);
                                valueCounts.put(mappedValue, valueCounts.getOrDefault(mappedValue, 0) + 1);
                            }
                        }
                    }

                    // 빈도수를 rows 리스트로 변환
                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (Map.Entry<String, Integer> entry : valueCounts.entrySet()) {
                        rows.add(Map.of("value", entry.getKey(), "count", entry.getValue()));
                    }

                    result.put("rows", rows);
                    responseList.add(result);
                }
            }

        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }

        return responseList;
    }

    // 지역명 매핑 함수 추가
    private String mapRegionName(String label) {
        if (label.contains("서울")) return "서울특별시";
        if (label.contains("경기")) return "경기도";
        if (label.contains("인천")) return "인천광역시";
        if (label.contains("부산")) return "부산광역시";
        if (label.contains("대구")) return "대구광역시";
        if (label.contains("광주")) return "광주광역시";
        if (label.contains("대전")) return "대전광역시";
        if (label.contains("울산")) return "울산광역시";
        if (label.contains("세종")) return "세종특별자치시";
        if (label.contains("강원")) return "강원도";
        if (label.contains("충청북도") || label.contains("충북")) return "충청북도";
        if (label.contains("충청남도") || label.contains("충남")) return "충청남도";
        if (label.contains("전라북도") || label.contains("전북")) return "전라북도";
        if (label.contains("전라남도") || label.contains("전남")) return "전라남도";
        if (label.contains("경상북도") || label.contains("경북")) return "경상북도";
        if (label.contains("경상남도") || label.contains("경남")) return "경상남도";
        if (label.contains("제주")) return "제주특별자치도";
        return "기타지역"; // 매핑되지 않으면 원래 이름 반환
    }

    private List<Map<String, String>> processFileWithFilters(File excelFile, Map<String, String> filterConditions, List<String> headers) throws IOException {
        List<Map<String, String>> filteredData = new ArrayList<>();

        try (InputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            int numberOfSheets = workbook.getNumberOfSheets();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                if (!sheet.getSheetName().contains("CRF")) {
                    continue; // "CRF"가 없는 Sheet는 건너뛰기
                }

                Row headerRow = sheet.getRow(3); // 4번째 행을 헤더로 가정
                if (headerRow == null) {
                    continue;
                }

                // 헤더 행의 인덱스 매핑
                Map<String, Integer> headerIndexMap = new HashMap<>();
                for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
                    Cell cell = headerRow.getCell(cellIndex);
                    if (cell != null) {
                        String headerName = cell.getStringCellValue().trim();
                        headerIndexMap.put(headerName, cellIndex);  // 모든 헤더 저장
                    }
                }

                // 모든 데이터를 먼저 필터링
                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {  // 9번째 행부터 데이터 읽기
                    Row row = sheet.getRow(rowIndex);
                    if (row != null && matchesConditions(row, headerIndexMap, filterConditions)) {
                        Map<String, String> rowData = new LinkedHashMap<>();

                        // Tooth 관련 필드 확인
                        if (headers.contains("Tooth")) {
                            // Tooth_로 시작하는 모든 헤더에 대해 값을 가져옴
                            for (Map.Entry<String, Integer> entry : headerIndexMap.entrySet()) {
                                String headerName = entry.getKey();
                                if (headerName.startsWith("Tooth_")) {
                                    Integer cellIndex = entry.getValue();
                                    Cell cell = row.getCell(cellIndex);
                                    String cellValue = (cell != null) ? getCellValueAsString(cell).trim() : "";
                                    rowData.put(headerName, cellValue);
                                }
                            }
                        }

                        // 일반 헤더 처리
                        for (String header : headers) {
                            if (!header.contains("Tooth")) {
                                Integer cellIndex = headerIndexMap.get(header);
                                if (cellIndex != null) {
                                    Cell cell = row.getCell(cellIndex);
                                    String cellValue = (cell != null) ? getCellValueAsString(cell) : "";
                                    rowData.put(header, cellValue);
                                }
                            }
                        }

                        if (!rowData.isEmpty()) {
                            filteredData.add(rowData);
                        }
                    }
                }
            }
        }
        return filteredData;
    }


    // 조건과 일치하는지 확인하는 메소드
    private boolean matchesConditions(Row row, Map<String, Integer> headerIndexMap, Map<String, String> filterConditions) {
        for (Map.Entry<String, String> condition : filterConditions.entrySet()) {
            String header = condition.getKey();
            String expectedValue = condition.getValue();
            Integer cellIndex = headerIndexMap.get(header);

            if (cellIndex != null) {
                String cellValue = getCellValueAsString(row.getCell(cellIndex));

                // 숫자 범위 조건의 경우, 범위 값에 맞는지 확인
                switch (header) {
                    case "P_AGE" -> {
                        if (!matchesAgeCondition(cellValue, expectedValue)) {
                            return false;
                        }
                    }
                    case "P_WEIGHT" -> {
                        if (!matchesWeightCondition(cellValue, expectedValue)) {
                            return false;
                        }
                    }
                    case "P_HEIGHT" -> {
                        if (!matchesHeightCondition(cellValue, expectedValue)) {
                            return false;
                        }
                    }
                    case "CAPTURE_TIME" -> {
                        if (!matchesYearRangeCondition(cellValue, expectedValue)) {
                            return false;
                        }
                    }
                    default -> {
                        // 기본 문자열 비교
                        if (!cellValue.equals(expectedValue)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true; // 모든 조건을 만족하면 true 반환
    }


    // 나이 필터링 로직 (P_AGE)
    private boolean matchesAgeCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false 반환
        }

        int age = Integer.parseInt(actualValue);
        return switch (expectedSendValue) {
            case "0" -> age < 10;
            case "1" -> age >= 10 && age <= 20;
            case "2" -> age >= 21 && age <= 30;
            case "3" -> age >= 31 && age <= 40;
            case "4" -> age >= 41 && age <= 50;
            case "5" -> age >= 51 && age <= 60;
            case "6" -> age >= 61 && age <= 70;
            case "7" -> age >= 71 && age <= 80;
            case "8" -> age >= 81 && age <= 90;
            case "9" -> age > 90;
            default -> false;
        };
    }

    // 체중 필터링 로직 (P_WEIGHT)
    private boolean matchesWeightCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false 반환
        }

        int weight = Integer.parseInt(actualValue);
        return switch (expectedSendValue) {
            case "0" -> weight < 40;
            case "1" -> weight >= 40 && weight <= 50;
            case "2" -> weight >= 51 && weight <= 60;
            case "3" -> weight >= 61 && weight <= 70;
            case "4" -> weight >= 71 && weight <= 80;
            case "5" -> weight >= 81 && weight <= 90;
            case "6" -> weight > 90;
            default -> false;
        };
    }

    // 키 필터링 로직 (P_HEIGHT)
    private boolean matchesHeightCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false를 반환
        }

        int height = Integer.parseInt(actualValue);
        return switch (expectedSendValue) {
            case "0" -> height < 140;
            case "1" -> height >= 141 && height <= 150;
            case "2" -> height >= 151 && height <= 160;
            case "3" -> height >= 161 && height <= 170;
            case "4" -> height >= 171 && height <= 180;
            case "5" -> height >= 181 && height <= 190;
            case "6" -> height > 190;
            default -> false;
        };
    }

    private boolean matchesYearRangeCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false를 반환
        }

        int year;
        try {
            year = Integer.parseInt(actualValue);
        } catch (NumberFormatException e) {
            return false;
        }


        return switch (expectedSendValue) {
            case "12" -> year >= 1201 && year <= 1212;
            case "13" -> year >= 1301 && year <= 1312;
            case "14" -> year >= 1401 && year <= 1412;
            case "15" -> year >= 1501 && year <= 1512;
            case "16" -> year >= 1601 && year <= 1612;
            case "17" -> year >= 1701 && year <= 1712;
            case "18" -> year >= 1801 && year <= 1812;
            case "19" -> year >= 1901 && year <= 1912;
            case "20" -> year >= 2001 && year <= 2012;
            case "21" -> year >= 2101 && year <= 2112;
            case "22" -> year >= 2201 && year <= 2212;
            case "23" -> year >= 2301 && year <= 2312;
            default -> false;
        };
    }





    // 셀 데이터를 String 변환하는 메소드
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
}
