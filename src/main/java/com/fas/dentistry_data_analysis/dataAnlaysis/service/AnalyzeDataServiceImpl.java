package com.fas.dentistry_data_analysis.dataAnlaysis.service;

import com.fas.dentistry_data_analysis.common.service.JSONService;
import com.fas.dentistry_data_analysis.dataAnlaysis.util.excel.ConditionMatcher;
import com.fas.dentistry_data_analysis.dataAnlaysis.util.excel.ExcelUtils;
import com.fas.dentistry_data_analysis.dataAnlaysis.util.excel.HeaderMapping;
import com.fas.dentistry_data_analysis.dataAnlaysis.util.excel.ValueMapping;
import com.fasterxml.jackson.databind.JsonNode;
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

import static com.fas.dentistry_data_analysis.dataAnlaysis.util.excel.ExcelUtils.getCellValueAsString;

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


    private static final Map<String, String> DiseaseClassMap = new HashMap<>() {{
        put("A", "치주질환");
        put("B", "골수염");
        put("C", "구강암");
        put("D", "두개안면");
        put("E", "대조군");
    }};

    private static final Map<String, String> InstitutionMap = new HashMap<>() {{
        put("1", "원광대학교");
        put("2", "고려대학교");
        put("3", "서울대학교");
        put("4", "국립암센터");
        put("5", "단국대학교");
        put("6", "조선대학교");
        put("7", "보라매병원");
    }};

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

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("지정된 경로가 유효하지 않거나 폴더가 아닙니다: " + folderPath);
        }

        String institutionKeyword = institutionId == 0 ? "" : InstitutionMap.getOrDefault(String.valueOf(institutionId), "");
        Set<String> passIdsSet = new HashSet<>(jsonService.loadPassIdsFromJson("C:/app/id/pass_ids.json"));
        Set<String> IdSet = new HashSet<>();
        Set<String> processedFiles = new HashSet<>();

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<List<Map<String, Map<String, String>>>>> futureResults = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            throw new IOException("지정된 폴더에 파일이 없습니다.");
        }

        for (File file : files) {
            if (!file.isFile() || !processedFiles.add(file.getName())) {
                continue; // 이미 처리된 파일은 건너뜀
            }

            String fileName = file.getName();

            // diseaseClass가 0인 경우 파일 이름을 기준으로 diseaseClass 동적으로 설정
            String determinedClass = "0".equals(diseaseClass) ? determineDiseaseClassFromFile(fileName) : diseaseClass;

            // 1. 대조군(E) 처리 우선순위
            if ("0".equals(diseaseClass) && fileName.contains("대조군")) {
                determinedClass = "E"; // 전체 분석 시 대조군 우선 처리
            }

            // 2. 현재 diseaseClass가 대조군(E)이 아닌 경우, "대조군" 키워드가 포함된 파일 제외
            if (!"E".equals(determinedClass) && fileName.contains("대조군")) {
                continue;
            }

            if (determinedClass.isEmpty()) {
                continue; // 파일 이름에서 diseaseClass를 설정하지 못한 경우 건너뜀
            }

            String diseaseKeyword = DiseaseClassMap.getOrDefault(determinedClass, "");
            final String dynamicDiseaseClass = determinedClass; // 람다 표현식에서 사용 가능하도록 final 변수로 설정

            // 파일 이름과 institutionKeyword로 필터링
            if (!isFileMatchingCriteria(fileName, diseaseKeyword, institutionKeyword)) {
                continue; // 필터 조건에 맞지 않으면 건너뜀
            }

            futureResults.add(executor.submit(() ->
                    fileProcessor.processServerFile(file, dynamicDiseaseClass, institutionId, passIdsSet, IdSet)
            ));
        }

        List<Map<String, Map<String, String>>> combinedData = new ArrayList<>();
        for (Future<List<Map<String, Map<String, String>>>> future : futureResults) {
            combinedData.addAll(future.get());
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

        String institutionKeyword = institutionId == 0 ? "" : InstitutionMap.getOrDefault(String.valueOf(institutionId), "");
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

            // 파일 필터링
            String fileName = file.getName();
            // diseaseClass가 0인 경우 파일 이름을 기준으로 diseaseClass 동적으로 설정
            String dynamicDiseaseClass = "0".equals(diseaseClass) ? determineDiseaseClassFromFile(fileName) : diseaseClass;
            if (dynamicDiseaseClass.isEmpty()) {
                continue; // 파일 이름에서 diseaseClass를 설정하지 못한 경우 건너뜀
            }

            String diseaseKeyword = DiseaseClassMap.getOrDefault(dynamicDiseaseClass, "");

            // 파일 이름과 institutionKeyword로 필터링
            if (!isFileMatchingCriteria(fileName, diseaseKeyword, institutionKeyword)) {
                continue; // 필터 조건에 맞지 않으면 건너뜀
            }

            // 각 파일을 처리하는 작업을 병렬로 실행
            futureResults.add(executor.submit(() ->
                    jsonFileProcessor.processJsonFile(file, dynamicDiseaseClass, institutionId)
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
        if (folderPath == null) {
            throw new IllegalArgumentException("파일 ID 목록이 비어있거나 null입니다.");
        }

        // 폴더가 유효한지 확인
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("지정된 경로가 유효하지 않거나 폴더가 아닙니다: " + folderPath);
        }

        // DISEASE_CLASS에 따라 폴더 필터링
        String diseaseClass = filterConditions.get("DISEASE_CLASS");
        String institutionID = filterConditions.get("INSTITUTION_ID");

        String targetDiseaseName;
        String targetInstitutionName;

        // 질환명 확인
        if (diseaseClass != null && !"All".equalsIgnoreCase(diseaseClass)) {
            if (!DiseaseClassMap.containsKey(diseaseClass)) {
                throw new IllegalArgumentException("유효하지 않은 DISEASE_CLASS: " + diseaseClass);
            }
            targetDiseaseName = DiseaseClassMap.get(diseaseClass);
        } else {
            targetDiseaseName = null;
        }
        // 기관명 확인
        if (institutionID != null && !institutionID.isEmpty()) {
            if (!InstitutionMap.containsKey(institutionID)) {
                throw new IllegalArgumentException("유효하지 않은 INSTITUTION_ID: " + institutionID);
            }
            targetInstitutionName = InstitutionMap.get(institutionID);
        } else {
            targetInstitutionName = null;
        }

        // 전체 또는 특정 질환 및 기관에 해당하는 파일 필터링
        File[] files = folder.listFiles(file -> {
            if (file.isDirectory() || !file.getName().toLowerCase().endsWith(".xlsx")) {
                return false;
            }
            boolean matchesDisease = (targetDiseaseName == null) || file.getName().contains(targetDiseaseName);
            boolean matchesInstitution = (targetInstitutionName == null) || file.getName().contains(targetInstitutionName);
            return matchesDisease && matchesInstitution;
        });

        if (files == null || files.length == 0) {
            log.warn("조건에 맞는 파일이 없습니다. 질환: {}, 기관: {}", targetDiseaseName, targetInstitutionName);
            return Collections.emptyList();
        }

        // passIdsSet 로드
        Set<String> passIdsSet = new HashSet<>(jsonService.loadPassIdsFromJson("C:/app/id/pass_ids.json"));
        Set<String> processedIds = new HashSet<>();

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
                    List<Map<String, String>> fileData = processFolderFileWithFilters(file, fileFilterConditions, headers,passIdsSet,processedIds);
                    return fileData;
                }));
            }

            // 기존 헤더 기반 로직 처리
            for (String header : headers) {
                if ("Tooth".equals(header)) {
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
                                        case "1": normalCount++; break;
                                        case "2": prosthesisCount++; break;
                                        case "3": implantCount++; break;
                                        case "4": bridgeCount++; break;
                                        case "5": case "6": otherCount++; break;
                                    }
                                }
                            }
                        }
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    rows.add(Map.of("value", "정상", "count", normalCount));
                    rows.add(Map.of("value", "보철", "count", prosthesisCount));
                    rows.add(Map.of("value", "임플란트", "count", implantCount));
                    rows.add(Map.of("value", "브릿지", "count", bridgeCount));
                    rows.add(Map.of("value", "기타", "count", otherCount));

                    result.put("rows", rows);
                    responseList.add(result);
                } else if ("P_RES_AREA".equals(header)) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("headers", HeaderMapping.determineHeadersBasedOnFilters(Collections.singletonList(header)));
                    result.put("id", "P_RES_AREA");
                    result.put("title", HeaderMapping.determineTitleBasedOnHeaders(Collections.singletonList(header)));

                    Map<String, Integer> regionCounts = new HashMap<>();

                    for (Future<List<Map<String, String>>> future : futures) {
                        List<Map<String, String>> fileData = future.get();

                        for (Map<String, String> rowData : fileData) {
                            String region = rowData.getOrDefault("P_RES_AREA", "").trim();
                            if (!region.isEmpty()) {
                                String mappedRegion = mapRegionName(region);
                                regionCounts.put(mappedRegion, regionCounts.getOrDefault(mappedRegion, 0) + 1);
                            }
                        }
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (Map.Entry<String, Integer> entry : regionCounts.entrySet()) {
                        rows.add(Map.of("value", entry.getKey(), "count", entry.getValue()));
                    }

                    result.put("rows", rows);
                    responseList.add(result);
                } else {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", header);
                    result.put("title", HeaderMapping.determineTitleBasedOnHeaders(Collections.singletonList(header)));
                    result.put("headers", HeaderMapping.determineHeadersBasedOnFilters(Collections.singletonList(header)));

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

                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (Map.Entry<String, Integer> entry : valueCounts.entrySet()) {
                        rows.add(Map.of("value", entry.getKey(), "count", entry.getValue()));
                    }

                    result.put("rows", rows);
                    responseList.add(result);
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("파일 처리 중 오류가 발생했습니다.", e);
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
            String fileName = excelFile.getName();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                // 기존 시트 이름 필터링 로직 유지
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

    private JsonNode findValueInSections(JsonNode recordNode, String key) {
        // 입력 키를 공백 제거
        String sanitizedKey = key.replaceAll("\\s+", "");

        // JSON 최상위에서 값 검색
        Iterator<String> rootFieldNames = recordNode.fieldNames();
        while (rootFieldNames.hasNext()) {
            String fieldName = rootFieldNames.next();
            if (fieldName.replaceAll("\\s+", "").equals(sanitizedKey)) {
                return recordNode.get(fieldName); // 매칭된 값 반환
            }
        }

        // JSON 섹션에서 값 검색
        Iterator<Map.Entry<String, JsonNode>> fields = recordNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode section = field.getValue();

            if (section.isArray()) { // 섹션이 배열인 경우
                for (JsonNode item : section) {
                    Iterator<String> itemFieldNames = item.fieldNames();
                    while (itemFieldNames.hasNext()) {
                        String itemFieldName = itemFieldNames.next();
                        if (itemFieldName.replaceAll("\\s+", "").equals(sanitizedKey)) {
                            return item.get(itemFieldName); // 매칭된 값 반환
                        }
                    }
                }
            } else if (section.isObject()) { // 섹션이 객체인 경우
                Iterator<String> sectionFieldNames = section.fieldNames();
                while (sectionFieldNames.hasNext()) {
                    String sectionFieldName = sectionFieldNames.next();
                    if (sectionFieldName.replaceAll("\\s+", "").equals(sanitizedKey)) {
                        return section.get(sectionFieldName); // 매칭된 값 반환
                    }
                }
            }
        }

        return null; // 값을 찾을 수 없으면 null 반환
    }



    private static final Map<String, String> HeaderSynonyms = new HashMap<>() {{
        put("MAKER_INFO", "MAKER_INFO");
        put("MAKER_INFO", "MAKER_IF"); // MAKER_IF를 MAKER_INFO로 매핑
    }};

    private List<Map<String, String>> processJsonFileWithFilters(File jsonFile, Map<String, String> filterConditions, List<String> headers, Set<String> processedIds) throws IOException {
        List<Map<String, String>> filteredData = new ArrayList<>();
        // 파일명에서 기관명 추출
        String institutionName = extractInstitutionName(jsonFile.getName());
        ObjectMapper objectMapper = new ObjectMapper();

        try (InputStream inputStream = new FileInputStream(jsonFile)) {
            // JSON 파일 파싱
            JsonNode rootNode = objectMapper.readTree(inputStream);

            // JSON이 배열인지 확인
            if (!rootNode.isArray()) {
                throw new IllegalArgumentException("JSON 파일이 배열 형식이어야 합니다: " + jsonFile.getName());
            }

            for (JsonNode recordNode : rootNode) {
                // IMAGE_ID 또는 Identifier의 IMAGE_ID 값을 추출
                String imageId = null;

                // IMAGE_ID 직접 추출
                JsonNode imageIdNode = findValueInSections(recordNode, "IMAGE_ID");
                if (imageIdNode != null && !imageIdNode.asText().trim().isEmpty()) {
                    imageId = imageIdNode.asText().trim();
                }

                // Identifier 추출
                if (imageId == null) {
                    JsonNode identifierNode = recordNode.get("Identifier");
                    if (identifierNode != null) {
                        if (identifierNode.isArray()) { // 배열인 경우
                            for (JsonNode item : identifierNode) {
                                JsonNode identifierImageIdNode = item.get("IMAGE_ID");
                                if (identifierImageIdNode != null && !identifierImageIdNode.asText().trim().isEmpty()) {
                                    imageId = identifierImageIdNode.asText().trim();
                                    break;
                                }
                            }
                        } else if (identifierNode.isTextual()) { // 단일 문자열인 경우
                            imageId = identifierNode.asText().trim();
                        }
                    }
                }

                if (imageId == null || imageId.isEmpty()) {
                    continue;
                }

                // 데이터 추가
                Map<String, String> rowData = new LinkedHashMap<>();
                rowData.put("IMAGE_ID", imageId);

                // 헤더 처리
                for (String header : headers) {
                    // 헤더 매핑 처리 및 trim() 적용
                    String normalizedHeader = HeaderSynonyms.getOrDefault(header.trim(), header.trim());

                    if ("Tooth".equals(normalizedHeader)) {
                        // "Annotation_Data" 섹션에서 숫자 키에 해당하는 값 추출
                        JsonNode annotationDataNode = recordNode.get("Annotation_Data");
                        if (annotationDataNode != null && annotationDataNode.isArray()) {
                            for (JsonNode annotationItem : annotationDataNode) {
                                Iterator<String> fieldNames = annotationItem.fieldNames();
                                while (fieldNames.hasNext()) {
                                    String fieldName = fieldNames.next().trim(); // 키값 trim()
                                    // 숫자 키인지 확인
                                    if (fieldName.matches("\\d+")) {
                                        String value = annotationItem.get(fieldName).asText().trim();
                                        rowData.put("Tooth_" + fieldName, value);
                                    }
                                }
                            }
                        }
                    } else if ("INSTITUTION_ID".equals(normalizedHeader)) {
                        rowData.put("INSTITUTION_ID", institutionName);
                    } else {
                        JsonNode valueNode = findValueInSections(recordNode, normalizedHeader);
                        String value = (valueNode != null) ? valueNode.asText().trim() : "none";
                        // 값이 null이거나 "none"일 경우 건너뜀
                        if (value == null || value.equalsIgnoreCase("none")) {
                            continue;
                        }
                        rowData.put(header.trim(), value); // 헤더도 trim() 적용
                    }
                }

                // 결과 추가
                if (!rowData.isEmpty()) {
                    filteredData.add(rowData);
                }
            }
        }
        return filteredData;
    }

    @Override
    public List<Map<String, Object>> analyzeJsonDataWithFilters(String folderPath, Map<String, String> filterConditions, List<String> headers) {
        if (folderPath == null) {
            throw new IllegalArgumentException("파일 ID 목록이 비어있거나 null입니다.");
        }

        // 폴더가 유효한지 확인
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("지정된 경로가 유효하지 않거나 폴더가 아닙니다: " + folderPath);
        }

        // DISEASE_CLASS에 따라 폴더 필터링
        String diseaseClass = filterConditions.get("DISEASE_CLASS");
        String institutionID = filterConditions.get("INSTITUTION_ID");

        String targetDiseaseName;
        String targetInstitutionName;

        // 질환명 확인
        if (diseaseClass != null && !"All".equalsIgnoreCase(diseaseClass)) {
            if (!DiseaseClassMap.containsKey(diseaseClass)) {
                throw new IllegalArgumentException("유효하지 않은 DISEASE_CLASS: " + diseaseClass);
            }
            targetDiseaseName = DiseaseClassMap.get(diseaseClass);
        } else {
            targetDiseaseName = null;
        }
        // 기관명 확인
        if (institutionID != null && !institutionID.isEmpty()) {
            if (!InstitutionMap.containsKey(institutionID)) {
                throw new IllegalArgumentException("유효하지 않은 INSTITUTION_ID: " + institutionID);
            }
            targetInstitutionName = InstitutionMap.get(institutionID);
        } else {
            targetInstitutionName = null;
        }

        // 전체 또는 특정 질환 및 기관에 해당하는 파일 필터링
        File[] files = folder.listFiles(file -> {
            if (file.isDirectory() || !file.getName().toLowerCase().endsWith(".json")) {
                return false;
            }
            boolean matchesDisease = (targetDiseaseName == null) || file.getName().contains(targetDiseaseName);
            boolean matchesInstitution = (targetInstitutionName == null) || file.getName().contains(targetInstitutionName);
            return matchesDisease && matchesInstitution;
        });

        if (files == null || files.length == 0) {
            log.warn("조건에 맞는 파일이 없습니다. 질환: {}, 기관: {}", targetDiseaseName, targetInstitutionName);
            return Collections.emptyList();
        }

        Set<String> processedIds = new HashSet<>();
        List<Map<String, Object>> responseList = new ArrayList<>();
        ExecutorService executor = Executors.newCachedThreadPool();

        try {
            // 비동기 파일 처리
            List<Future<List<Map<String, String>>>> futures = new ArrayList<>();
            for (File file : files) {
                futures.add(executor.submit(() -> {
                    Map<String, String> fileFilterConditions = new HashMap<>(filterConditions);
                    if ("All".equalsIgnoreCase(fileFilterConditions.get("DISEASE_CLASS"))) {
                        fileFilterConditions.remove("DISEASE_CLASS");
                    }
                    return processJsonFileWithFilters(file, fileFilterConditions, headers, processedIds);
                }));
            }

            // 기존 헤더 기반 로직 처리
            for (String header : headers) {
                if ("Tooth".equals(header)) {
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
                                        case "1": normalCount++; break;
                                        case "2": prosthesisCount++; break;
                                        case "3": implantCount++; break;
                                        case "4": bridgeCount++; break;
                                        case "5": case "6": otherCount++; break;
                                    }
                                }
                            }
                        }
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    rows.add(Map.of("value", "정상", "count", normalCount));
                    rows.add(Map.of("value", "보철", "count", prosthesisCount));
                    rows.add(Map.of("value", "임플란트", "count", implantCount));
                    rows.add(Map.of("value", "브릿지", "count", bridgeCount));
                    rows.add(Map.of("value", "기타", "count", otherCount));

                    result.put("rows", rows);
                    responseList.add(result);
                } else {
                    // 기존 로직 처리
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", header);
                    result.put("title", HeaderMapping.determineTitleBasedOnHeaders(Collections.singletonList(header)));
                    result.put("headers", HeaderMapping.determineHeadersBasedOnFilters(Collections.singletonList(header)));

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

                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (Map.Entry<String, Integer> entry : valueCounts.entrySet()) {
                        rows.add(Map.of("value", entry.getKey(), "count", entry.getValue()));
                    }

                    result.put("rows", rows);
                    responseList.add(result);
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("파일 처리 중 오류가 발생했습니다.", e);
        } finally {
            executor.shutdown();
        }

        return responseList;
    }


    private List<Map<String, String>> processFolderFileWithFilters(File excelFile, Map<String, String> filterConditions, List<String> headers, Set<String> passIdsSet, Set<String> processedIds) throws IOException {
        List<Map<String, String>> filteredData = new ArrayList<>();

        // 파일명에서 기관명 추출
        String institutionName = extractInstitutionName(excelFile.getName());

        try (InputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            int numberOfSheets = workbook.getNumberOfSheets();
            String fileName = excelFile.getName();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                // 기존 시트 이름 필터링 로직 유지
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
                        headerIndexMap.put(headerName, cellIndex);
                    }
                }
                Integer imageIdIndex = headerIndexMap.get("IMAGE_ID");
                if (imageIdIndex == null) {
                    throw new IllegalArgumentException("IMAGE_ID 헤더가 누락되었습니다. 파일을 확인하세요: " + excelFile.getName());
                }

                // 모든 데이터를 필터링
                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) { // 9번째 행부터 데이터 읽기
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    // IMAGE_ID 필터링
                    String imageId = getCellValueAsString(row.getCell(imageIdIndex)).trim();

                    // 중복 IMAGE_ID 처리
                    synchronized (processedIds) {
                        if (!passIdsSet.contains(imageId) || !processedIds.add(imageId)) {
                            continue; // passIdsSet에 없거나 이미 처리된 IMAGE_ID는 건너뜀
                        }
                    }

                    if (matchesConditions(row, headerIndexMap, filterConditions)) {
                        Map<String, String> rowData = new LinkedHashMap<>();

                        // Tooth 관련 필드 확인
                        if (headers.contains("Tooth")) {
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
                                    String cellValue = (cell != null) ? getCellValueAsString(cell).trim() : "";
                                    rowData.put(header, cellValue);
                                }
                            }
                        }

                        // 기관명 추가
                        if (headers.contains("INSTITUTION_ID")) {
                            rowData.put("INSTITUTION_ID", institutionName);
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


    /**
     * 파일명에서 기관명을 추출하는 메서드.
     * @param fileName 파일명
     * @return 추출된 기관명
     */
    private String extractInstitutionName(String fileName) {
        // 확장자 제거
        String cleanFileName = fileName.replace(".json", "");

        // 파일명 분리 및 기관명 추출
        String[] parts = cleanFileName.split("_");
        if (parts.length > 1) {
            return parts[1]; // 두 번째 요소를 기관명으로 간주
        }
        return "알 수 없는 기관"; // 기본 값
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

    private String determineDiseaseClassFromFile(String fileName) {
        // 파일 이름을 여러 구분자로 분리 (언더스코어, 점, 공백, 하이픈 등)
        String[] parts = fileName.split("[_\\.\\-\\s]+");

        // 1. "대조군"을 가장 먼저 처리
        for (String part : parts) {
            if (part.contains("대조군")) {
                return "E"; // 대조군(E)을 우선 처리
            }
        }

        // 2. 나머지 질환 키워드 처리
        for (Map.Entry<String, String> entry : DiseaseClassMap.entrySet()) {
            for (String part : parts) {
                if (part.contains(entry.getValue())) {
                    return entry.getKey(); // 첫 번째 매칭된 질환 반환
                }
            }
        }

        // 3. 매칭되지 않으면 빈 문자열 반환
        return "";
    }


    private boolean isFileMatchingCriteria(String fileName, String diseaseKeyword, String institutionKeyword) {
        boolean matchesDisease = diseaseKeyword.isEmpty() || fileName.contains(diseaseKeyword);
        boolean matchesInstitution = institutionKeyword.isEmpty() || fileName.contains(institutionKeyword);
        return matchesDisease && matchesInstitution;
    }
}
