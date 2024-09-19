package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.config.SheetHeaderMapping;
import com.fas.dentistry_data_analysis.util.HeaderMappingService;
import com.fas.dentistry_data_analysis.util.ValueMappingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

@Slf4j
@Service
public class ExcelUploadService{

    // 파일 ID와 파일 경로를 매핑하는 Map
    private final Map<String, Path> fileStorage = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());


    // 파일을 서버에 저장하고 고유한 파일 ID를 반환하는 메소드
    public String storeFile(MultipartFile file) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String tempDir = System.getProperty("java.io.tmpdir");
        // 파일명에 포함된 공백이나 특수 문자를 제거
        String sanitizedFileName = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
        Path filePath = Paths.get(tempDir, fileId + "_" + sanitizedFileName);

        try {
            // 파일 저장
            Files.write(filePath, file.getBytes());
            // 파일이 제대로 저장되었는지 확인하는 로그
            if (Files.exists(filePath)) {
                System.out.println("파일 저장 성공: " + filePath.toString());
            } else {
                System.out.println("파일 저장 실패: " + filePath.toString());
            }

            // 파일 ID와 경로를 저장
            fileStorage.put(fileId, filePath);
        } catch (IOException e) {
            System.err.println("파일 저장 중 오류 발생: " + e.getMessage());
            throw e;
        }

        return fileId; // 고유한 파일 ID 반환
    }

    // 다중 파일 ID를 기반으로 데이터 분석을 수행하는 메소드
    public List<Map<String, String>> analyzeData(String[] fileIds, String diseaseClass, int institutionId) throws IOException, InterruptedException, ExecutionException {
        if (fileIds == null || fileIds.length == 0) {
            throw new IllegalArgumentException("파일 ID 목록이 비어있거나 null입니다.");
        }

        List<Future<List<Map<String, String>>>> futureResults = new ArrayList<>();
        for (String fileId : fileIds) {
            Path filePath = fileStorage.get(fileId);
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
    public List<Map<String, String>> processFile(File file, String diseaseClass, int institutionId) throws IOException, ExecutionException, InterruptedException {
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

        try (InputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {  // SXSSFWorkbook 사용 (XSSFWorkbook 스트리밍 모드)

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
                    int institutionIdValue;

                    if (diseaseClassIndex != null && institutionIdIndex != null) {
                        for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {  // 9번째 행부터 데이터 읽기
                            Row row = sheet.getRow(rowIndex);
                            if (row != null) {
                                String diseaseClassValue = getCellValueAsString(row.getCell(diseaseClassIndex));
                                String institutionIdValueStr = getCellValueAsString(row.getCell(institutionIdIndex));

                                if (!institutionIdValueStr.isEmpty()) {
                                    try {
                                        institutionIdValue = Integer.parseInt(institutionIdValueStr);
                                    } catch (NumberFormatException e) {
                                        System.err.println("숫자로 변환할 수 없는 institutionId 값: " + institutionIdValueStr);
                                        institutionIdValue = -1; // 잘못된 값은 -1로 설정
                                    }

                                    if (diseaseClassValue.equals(diseaseClass) && institutionIdValue == institutionId) {
                                        Map<String, String> rowData = new LinkedHashMap<>();
                                        for (String header : expectedHeaders) {
                                            Integer cellIndex = headerIndexMap.get(header);
                                            if (cellIndex != null) {
                                                Cell cell = row.getCell(cellIndex);
                                                String cellValue = (cell != null) ? getCellValueAsString(cell) : "";
                                                rowData.put(header, cellValue);
                                            }
                                        }
                                        if (!rowData.isEmpty()) {
                                            dataList.add(rowData);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("엑셀 파일 처리 중 오류 발생: " + e.getMessage());
            throw e;
        }
        return dataList;
    }

    // 동적 필터링을 위한 메소드
    public List<Map<String, Object>> analyzeDataWithFilters(String[] fileIds, Map<String, String> filterConditions, List<String> headers) throws IOException {
        if (fileIds == null || fileIds.length == 0) {
            throw new IllegalArgumentException("파일 ID 목록이 비어있거나 null입니다.");
        }

        List<Map<String, Object>> responseList = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(4); // 스레드풀 생성

        try {
            // 비동기 파일 처리
            List<Future<List<Map<String, String>>>> futures = new ArrayList<>();
            for (String fileId : fileIds) {
                futures.add(executor.submit(() -> {
                    Path filePath = fileStorage.get(fileId);
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
                Map<String, Object> result = new HashMap<>();
                String title = HeaderMappingService.determineTitleBasedOnHeaders(Arrays.asList(header));
                List<String> dynamicHeaders = HeaderMappingService.determineHeadersBasedOnFilters(Arrays.asList(header));
                result.put("id", header);
                result.put("title", title);
                result.put("headers", dynamicHeaders);

                // 빈도수 계산
                Map<String, Integer> valueCounts = new HashMap<>();

                for (Future<List<Map<String, String>>> future : futures) {
                    List<Map<String, String>> fileData;
                    try {
                        fileData = future.get();
                    } catch (Exception e) {
                        throw new IOException("파일 처리 중 오류 발생", e);
                    }

                    for (Map<String, String> rowData : fileData) {
                        String value = rowData.getOrDefault(header, "").trim();

                        // 필터 조건을 확인하되 필터링 실패해도 매핑을 시도
                        if (filterConditions.containsKey(header)) {
                            String filterValue = filterConditions.get(header);

                            // 필터 조건을 만족하지 않으면 매핑만 수행 (필터된 데이터를 따로 기록하고 싶다면 로직 추가)
                            if (!filterValue.equals(value)) {
                                // 여기서 필터 조건을 만족하지 않는 데이터를 처리하고 싶으면 별도 로직 추가 가능
                            }
                        }

                        if (!value.isEmpty()) {
                            String mappedValue = ValueMappingService.headerMappingFunctions
                                    .getOrDefault(header, Function.identity())
                                    .apply(value);

                            valueCounts.put(mappedValue, valueCounts.getOrDefault(mappedValue, 0) + 1);
                        }
                    }
                }

                // 빈도수를 rows 리스트로 변환
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : valueCounts.entrySet()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("value", entry.getKey());
                    row.put("count", entry.getValue());
                    rows.add(row);
                }

                result.put("rows", rows);
                responseList.add(result);
            }

        } finally {
            executor.shutdown();
        }

        return responseList;
    }


    // 동적 필터링을 처리하는 메소드 (기존)
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
                        // 요청된 헤더에 포함된 것만 매핑
                        headerIndexMap.put(headerName, cellIndex);
                    }
                }

                // 모든 데이터를 먼저 필터링
                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {  // 9번째 행부터 데이터 읽기
                    Row row = sheet.getRow(rowIndex);
                    if (row != null && matchesConditions(row, headerIndexMap, filterConditions)) {
                        Map<String, String> rowData = new LinkedHashMap<>();
                        // 필터링된 데이터에서 헤더에 맞는 값만 추출
                        for (String header : headers) {
                            Integer cellIndex = headerIndexMap.get(header);
                            if (cellIndex != null) {
                                Cell cell = row.getCell(cellIndex);
                                String cellValue = (cell != null) ? getCellValueAsString(cell) : "";
                                rowData.put(header, cellValue);
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

                // 숫자 범위 조건의 경우, send 값에 맞는지 확인
                if (header.equals("P_AGE")) {
                    if (!matchesAgeCondition(cellValue, expectedValue)) {
                        return false;
                    }
                } else if (header.equals("P_WEIGHT")) {
                    if (!matchesWeightCondition(cellValue, expectedValue)) {
                        return false;
                    }
                } else if (header.equals("P_HEIGHT")) {
                    if (!matchesHeightCondition(cellValue, expectedValue)) {
                        return false;
                    }
                } else if (header.equals("CAPTURE_TIME")) {
                    if(!matchesYearRangeCondition(cellValue, expectedValue)) {
                        return false;
                    }
                }
                else {
                    // 기본 문자열 비교
                    if (!cellValue.equals(expectedValue)) {
                        return false;
                    }
                }
            }
        }
        return true; // 모든 조건을 만족하면 true 반환
    }


    // 나이 필터링 로직 (P_AGE)
    private boolean matchesAgeCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false를 반환
        }

        int age = Integer.parseInt(actualValue);
        switch (expectedSendValue) {
            case "0": return age < 10;
            case "1": return age >= 10 && age <= 20;
            case "2": return age >= 21 && age <= 30;
            case "3": return age >= 31 && age <= 40;
            case "4": return age >= 41 && age <= 50;
            case "5": return age >= 51 && age <= 60;
            case "6": return age >= 61 && age <= 70;
            case "7": return age >= 71 && age <= 80;
            case "8": return age >= 81 && age <= 90;
            case "9": return age > 90;
            default: return false;
        }
    }

    // 체중 필터링 로직 (P_WEIGHT)
    private boolean matchesWeightCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false를 반환
        }

        int weight = Integer.parseInt(actualValue);
            switch (expectedSendValue) {
                case "0": return weight < 40;
                case "1": return weight >= 40 && weight <= 50;
                case "2": return weight >= 51 && weight <= 60;
                case "3": return weight >= 61 && weight <= 70;
                case "4": return weight >= 71 && weight <= 80;
                case "5": return weight >= 81 && weight <= 90;
                case "6": return weight > 90;
                default: return false;
            }
        }

    // 키 필터링 로직 (P_HEIGHT)
    private boolean matchesHeightCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false를 반환
        }

        int height = Integer.parseInt(actualValue);
        switch (expectedSendValue) {
            case "0": return height < 140;
            case "1": return height >= 141 && height <= 150;
            case "2": return height >= 151 && height <= 160;
            case "3": return height >= 161 && height <= 170;
            case "4": return height >= 171 && height <= 180;
            case "5": return height >= 181 && height <= 190;
            case "6": return height > 190;
            default: return false;
        }
    }

    private boolean matchesYearRangeCondition(String actualValue, String expectedSendValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false;  // 값이 비어있으면 false를 반환
        }

        int year = Integer.parseInt(actualValue);

        switch (expectedSendValue) {
            case "12": return year >= 1201 && year <= 1212;
            case "13": return year >= 1301 && year <= 1312;
            case "14": return year >= 1401 && year <= 1412;
            case "15": return year >= 1501 && year <= 1512;
            case "16": return year >= 1601 && year <= 1612;
            case "17": return year >= 1701 && year <= 1712;
            case "18": return year >= 1801 && year <= 1812;
            case "19": return year >= 1901 && year <= 1912;
            case "20": return year >= 2001 && year <= 2012;
            case "21": return year >= 2101 && year <= 2112;
            case "22": return year >= 2201 && year <= 2212;
            case "23": return year >= 2301 && year <= 2312;
            default: return false;
        }
    }





    // 셀 데이터를 String으로 변환하는 메소드
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