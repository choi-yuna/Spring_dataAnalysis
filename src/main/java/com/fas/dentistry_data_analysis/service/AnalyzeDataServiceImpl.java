package com.fas.dentistry_data_analysis.service;

import com.fas.dentistry_data_analysis.service.Json.JSONService;
import com.fas.dentistry_data_analysis.util.ConditionMatcher;
import com.fas.dentistry_data_analysis.util.ExcelUtils;
import com.fas.dentistry_data_analysis.util.HeaderMapping;
import com.fas.dentistry_data_analysis.util.ValueMapping;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fas.dentistry_data_analysis.util.ExcelUtils.getCellValueAsString;

@Slf4j
@Service
public class AnalyzeDataServiceImpl  implements AnalyzeDataService{

    private final FileStorageService fileStorageService;
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final FileProcessor fileProcessor;
    private final JsonFileProcessor jsonFileProcessor;
    private final JSONService jsonService;


    @Autowired
    public AnalyzeDataServiceImpl(FileStorageService fileStorageService, FileProcessor fileProcessor, JsonFileProcessor jsonFileProcessor,JSONService jsonService) {
        this.fileStorageService = fileStorageService;
        this.fileProcessor = fileProcessor;
        this.jsonFileProcessor = jsonFileProcessor;
        this.jsonService = jsonService;
    }

    @Override
// 다중 파일 ID를 기반으로 데이터 분석을 수행하는 메소드
    public List<Map<String, Map<String, String>>> analyzeData(String[] fileIds, String diseaseClass, int institutionId)
            throws IOException, InterruptedException, ExecutionException {
        if (fileIds == null || fileIds.length == 0) {
            throw new IllegalArgumentException("파일 ID 목록이 비어있거나 null입니다.");
        }

        List<Future<List<Map<String, Map<String, String>>>>> futureResults = new ArrayList<>();
        for (String fileId : fileIds) {
            Path filePath = fileStorageService.getFilePath(fileId);
            if (filePath == null) {
                throw new IOException("파일을 찾을 수 없습니다. 파일 ID: " + fileId);
            }

            // 각 파일을 병렬 처리하도록 스레드풀에 제출
            Future<List<Map<String, Map<String, String>>>> future = executor.submit(
                    () -> fileProcessor.processFile(new File(filePath.toString()), diseaseClass, institutionId)
            );
            futureResults.add(future);
        }

        // 모든 파일의 데이터를 합치기
        List<Map<String, Map<String, String>>> combinedData = new ArrayList<>();
        for (Future<List<Map<String, Map<String, String>>>> future : futureResults) {
            combinedData.addAll(future.get()); // 결과를 합침
        }

        return combinedData;
    }


    @Override
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


    @Override
    public List<Map<String, Map<String, String>>> analyzeFolderData(String folderPath, String diseaseClass, int institutionId)
            throws IOException, InterruptedException, ExecutionException {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new IllegalArgumentException("폴더 경로가 비어있거나 null입니다.");
        }

        // 폴더가 유효한지 확인
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("지정된 경로가 유효하지 않거나 폴더가 아닙니다: " + folderPath);
        }

        Set<String> passIdsSet = new HashSet<>(jsonService.loadPassIdsFromJson("C:/app/id/pass_ids.json"));
        Set<String> IdSet = new HashSet<>();

        // 이미 처리된 파일 추적용 Set
        Set<String> processedFiles = new HashSet<>();

        // 폴더 내의 모든 파일을 병렬로 처리
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<List<Map<String, Map<String, String>>>>> futureResults = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            throw new IOException("지정된 폴더에 파일이 없습니다.");
        }

        for (File file : files) {
            // 이미 처리된 파일은 건너뜀
            if (!file.isFile() || !processedFiles.add(file.getName())) {
                continue;
            }

            // 각 파일을 처리하는 작업을 병렬로 실행
            futureResults.add(executor.submit(() ->
                    fileProcessor.processServerFile(file, diseaseClass, institutionId,passIdsSet,IdSet)
            ));
        }

        // 병렬 처리 결과를 결합
        List<Map<String, Map<String, String>>> combinedData = new ArrayList<>();
        for (Future<List<Map<String, Map<String, String>>>> future : futureResults) {
            combinedData.addAll(future.get()); // 결과를 합침
        }

        executor.shutdown();
        return combinedData;
    }

    @Override
    public List<Map<String, Map<String, String>>> analyzeJsonData(String folderPath, String diseaseClass, int institutionId) throws IOException, ExecutionException, InterruptedException {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new IllegalArgumentException("폴더 경로가 비어있거나 null입니다.");
        }

        // 폴더가 유효한지 확인
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("지정된 경로가 유효하지 않거나 폴더가 아닙니다: " + folderPath);
        }

        // 이미 처리된 파일 추적용 Set
        Set<String> processedFiles = new HashSet<>();

        // 폴더 내의 모든 파일을 병렬로 처리
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<List<Map<String, Map<String, String>>>>> futureResults = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            throw new IOException("지정된 폴더에 파일이 없습니다.");
        }

        for (File file : files) {
            // 이미 처리된 파일은 건너뜀
            if (!file.isFile() || !processedFiles.add(file.getName())) {
                continue;
            }

            // 각 파일을 처리하는 작업을 병렬로 실행
            futureResults.add(executor.submit(() ->
                    jsonFileProcessor.processJsonFile(file, diseaseClass, institutionId)
            ));
        }

        // 병렬 처리 결과를 결합
        List<Map<String, Map<String, String>>> combinedData = new ArrayList<>();
        for (Future<List<Map<String, Map<String, String>>>> future : futureResults) {
            combinedData.addAll(future.get()); // 결과를 합침
        }

        executor.shutdown();
        return combinedData;
    }








    @Override
    public List<Map<String, Object>> analyzeFolderDataWithFilters(String folderPath, Map<String, String> filterConditions, List<String> headers) throws IOException {
        if (folderPath == null ) {
            throw new IllegalArgumentException("파일 ID 목록이 비어있거나 null입니다.");
        }


        // 폴더가 유효한지 확인
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("지정된 경로가 유효하지 않거나 폴더가 아닙니다: " + folderPath);
        }

        // 이미 처리된 파일 추적용 Set
        Set<String> processedFiles = new HashSet<>();

        // 폴더 내의 모든 파일을 병렬로 처리
        File[] files = folder.listFiles();
        List<Map<String, Object>> responseList = new ArrayList<>();

        ExecutorService executor = Executors.newCachedThreadPool();

        try {
                // 비동기 파일 처리
                List<Future<List<Map<String, String>>>> futures = new ArrayList<>();
            for (File file : files) {
                futures.add(executor.submit(() -> {
                    if (folderPath == null) {
                        throw new IOException("파일을 찾을 수 없습니다. 파일 ID: " + folderPath);
                    }
                    Map<String, String> fileFilterConditions = new HashMap<>(filterConditions);
                    if ("All".equals(fileFilterConditions.get("DISEASE_CLASS"))) {
                        fileFilterConditions.remove("DISEASE_CLASS");
                    }
                    return processFileWithFilters(file, fileFilterConditions, headers);
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
                                            case "5":
                                            case "6":  // 기타
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
                                    String cellValue = (cell != null) ? ExcelUtils.getCellValueAsString(cell).trim() : "";
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
                                    String cellValue = (cell != null) ? ExcelUtils.getCellValueAsString(cell) : "";
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

    private boolean matchesConditions(Row row, Map<String, Integer> headerIndexMap, Map<String, String> filterConditions) {
        for (Map.Entry<String, String> condition : filterConditions.entrySet()) {
            String header = condition.getKey();
            String expectedValue = condition.getValue();
            Integer cellIndex = headerIndexMap.get(header);

            if (cellIndex != null) {
                String cellValue = getCellValueAsString(row.getCell(cellIndex));

                // 숫자 범위 조건의 경우, 범위 값에 맞는지 확인
                switch (header) {
                    case "P_AGE" : {
                        if (!ConditionMatcher.matchesAgeCondition(cellValue, expectedValue)) {
                            return false;
                        }
                    }
                    case "P_WEIGHT" : {
                        if (!ConditionMatcher.matchesWeightCondition(cellValue, expectedValue)) {
                            return false;
                        }
                    }
                    case "P_HEIGHT" : {
                        if (!ConditionMatcher.matchesHeightCondition(cellValue, expectedValue)) {
                            return false;
                        }
                    }
                    case "CAPTURE_TIME" : {
                        if (!ConditionMatcher.matchesYearRangeCondition(cellValue, expectedValue)) {
                            return false;
                        }
                    }
                    default : {
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

}
