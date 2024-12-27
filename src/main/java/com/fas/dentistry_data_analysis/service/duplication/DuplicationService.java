package com.fas.dentistry_data_analysis.service.duplication;

import com.fas.dentistry_data_analysis.service.Json.JSONService;
import com.fas.dentistry_data_analysis.util.ExcelUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DuplicationService {

    private final JSONService jsonService;

    public DuplicationService(JSONService jsonService) {
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

    public List<Map<String, List<String>>> analyzeFolderData(String folderPath, String diseaseClass, int institutionId)
            throws IOException, InterruptedException, ExecutionException {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new IllegalArgumentException("폴더 경로가 비어있거나 null입니다.");
        }

        // 폴더 유효성 확인
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("지정된 경로가 유효하지 않거나 폴더가 아닙니다: " + folderPath);
        }

        String diseaseKeyword = DiseaseClassMap.getOrDefault(diseaseClass, "");
        String institutionKeyword = institutionId == 0 ? "" : InstitutionMap.getOrDefault(String.valueOf(institutionId), "");

        Set<String> passIdsSet = new HashSet<>(jsonService.loadPassIdsFromJson("C:/app/id/pass_ids.json"));

        // 글로벌 중복 확인 맵
        Map<String, List<String>> combinationMap = new HashMap<>();
        Map<String, List<String>> duplicateDetailsMap = new HashMap<>(); // 중복된 조합 저장

        // 처리된 파일 추적
        Set<String> processedFiles = new HashSet<>();

        // 병렬 처리
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<Void>> futureResults = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            throw new IOException("지정된 폴더에 파일이 없습니다.");
        }

        for (File file : files) {
            if (!file.isFile() || !processedFiles.add(file.getName())) {
                continue; // 이미 처리된 파일은 건너뜀
            }

            // 파일 필터링
            String fileName = file.getName();
            if (!isFileMatchingCriteria(fileName, diseaseKeyword, institutionKeyword)) {
                continue; // 필터 조건에 맞지 않으면 건너뜀
            }

            futureResults.add(executor.submit(() -> {
                processServerFile(file, diseaseClass, institutionId, passIdsSet, combinationMap, duplicateDetailsMap);
                return null;
            }));
        }

        for (Future<Void> future : futureResults) {
            future.get(); // 각 작업 완료 대기
        }

        executor.shutdown();
        return new ArrayList<>(duplicateDetailsMap.entrySet().stream()
                .map(entry -> Map.of(entry.getKey(), entry.getValue()))
                .toList());
    }

    private boolean isFileMatchingCriteria(String fileName, String diseaseKeyword, String institutionKeyword) {
        boolean matchesDisease = diseaseKeyword.isEmpty() || fileName.contains(diseaseKeyword);
        boolean matchesInstitution = institutionKeyword.isEmpty() || fileName.contains(institutionKeyword);
        return matchesDisease && matchesInstitution;
    }

    public void processServerFile(File file, String diseaseClass, int institutionId, Set<String> passIdsSet,
                                  Map<String, List<String>> combinationMap,
                                  Map<String, List<String>> duplicateDetailsMap) throws IOException {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".xlsx")) {
            findFilteredDuplicateImageIds(file, diseaseClass, institutionId, passIdsSet, combinationMap, duplicateDetailsMap);
        } else {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
        }
    }

    public void findFilteredDuplicateImageIds(File excelFile, String diseaseClass, int institutionId,
                                              Set<String> passIdsSet,
                                              Map<String, List<String>> combinationMap,
                                              Map<String, List<String>> duplicateDetailsMap) throws IOException {
        try (InputStream inputStream = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            int numberOfSheets = workbook.getNumberOfSheets();

            for (int i = 0; i < numberOfSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);

                if (!isValidSheet(sheet, excelFile.getName())) {
                    continue;
                }

                Row headerRow = sheet.getRow(3);
                if (headerRow == null) {
                    throw new RuntimeException("헤더 행이 없습니다. 파일을 확인해주세요.");
                }

                Map<String, Integer> headerIndexMap = getHeaderIndexMap(headerRow);
                validateHeaderIndices(headerIndexMap);

                Integer imageIdIndex = headerIndexMap.get("IMAGE_ID");
                Integer captureTimeIndex = headerIndexMap.get("CAPTURE_TIME");
                Integer pAgeIndex = headerIndexMap.get("P_AGE");
                Integer pGenderIndex = headerIndexMap.get("P_GENDER");

                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row != null) {
                        String imageId = ExcelUtils.getCellValueAsString(row.getCell(imageIdIndex)).trim();
                        if (!passIdsSet.contains(imageId)) continue;

                        String captureTime = ExcelUtils.getCellValueAsString(row.getCell(captureTimeIndex)).trim();
                        String pAge = ExcelUtils.getCellValueAsString(row.getCell(pAgeIndex)).trim();
                        String pGender = ExcelUtils.getCellValueAsString(row.getCell(pGenderIndex)).trim();

                        String filteredId = imageId.replaceAll("^[A-Z]_\\d{1,}_", "");
                        String combinationKey = String.join("|", filteredId, captureTime, pAge, pGender);

                        synchronized (combinationMap) {
                            List<String> existingIds = combinationMap.getOrDefault(combinationKey, new ArrayList<>());
                            existingIds.add(imageId);

                            if (existingIds.size() > 1) {
                                duplicateDetailsMap.put(combinationKey, new ArrayList<>(existingIds));
                            }

                            combinationMap.put(combinationKey, existingIds);
                        }
                    }
                }
            }
        }
    }

    private boolean isValidSheet(Sheet sheet, String fileName) {
        String sheetName = sheet.getSheetName();
        if (fileName.contains("두개안면")) return sheetName.contains("CRF") && sheetName.contains("두개안면기형");
        if (fileName.contains("치주질환")) return sheetName.contains("CRF") && sheetName.contains("치주질환");
        if (fileName.contains("구강암")) return sheetName.contains("CRF") && sheetName.contains("구강암");
        if (fileName.contains("골수염")) return sheetName.contains("CRF") && sheetName.contains("골수염");
        return sheetName.contains("CRF");
    }

    private Map<String, Integer> getHeaderIndexMap(Row headerRow) {
        Map<String, Integer> headerIndexMap = new HashMap<>();
        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            Cell cell = headerRow.getCell(cellIndex);
            if (cell != null) {
                String headerName = cell.getStringCellValue().trim();
                headerIndexMap.put(headerName, cellIndex);
            }
        }
        return headerIndexMap;
    }

    private void validateHeaderIndices(Map<String, Integer> headerIndexMap) {
        if (!headerIndexMap.containsKey("IMAGE_ID") || !headerIndexMap.containsKey("CAPTURE_TIME") ||
                !headerIndexMap.containsKey("P_AGE") || !headerIndexMap.containsKey("P_GENDER")) {
            throw new RuntimeException("필수 헤더 중 하나가 없습니다. (IMAGE_ID, CAPTURE_TIME, P_AGE, P_GENDER)");
        }
    }
    public List<Map<String, Object>> loadDuplicateDataFromJson( String jsonFolderPath, String diseaseClass, int institutionId) throws IOException {
        File folder = new File(jsonFolderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            return new ArrayList<>();
        }


        // 질환과 기관에 해당하는 키워드 가져오기
        String diseaseKeyword = diseaseClass.equals("0") ? "" : DiseaseClassMap.getOrDefault(diseaseClass, "");
        String institutionKeyword = institutionId == 0 ? "" : InstitutionMap.getOrDefault(String.valueOf(institutionId), "");

        List<Map<String, Object>> duplicateDataList = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        // JSON 파일 필터링
        File[] jsonFiles = folder.listFiles((dir, name) -> {
            boolean matchesDisease = diseaseKeyword.isEmpty() || name.contains(diseaseKeyword);
            boolean matchesInstitution = institutionKeyword.isEmpty() || name.contains(institutionKeyword);
            return name.endsWith(".json") && matchesDisease && matchesInstitution;
        });

        if (jsonFiles == null || jsonFiles.length == 0) {
            log.warn("지정된 경로에 조건에 맞는 JSON 파일이 없습니다: {} (질환: {}, 기관: {})", jsonFolderPath, diseaseClass, institutionId);
            return duplicateDataList; // 빈 리스트 반환
        }

        // JSON 파일 읽기
        for (File jsonFile : jsonFiles) {
            try (InputStream inputStream = new FileInputStream(jsonFile)) {
                Map<String, Object> data = objectMapper.readValue(inputStream, new TypeReference<>() {});
                duplicateDataList.add(data);
                log.info("Loaded JSON file: {}", jsonFile.getName());
            } catch (IOException e) {
                log.error("Error loading JSON file: {}", jsonFile.getName(), e);
            }
        }

        return duplicateDataList;
    }


    public Map<String, Object> extractAndCombineData(String folderPath, String diseaseClass, int institutionId)
            throws IOException, InterruptedException, ExecutionException {
        // 중복된 CRF 데이터 추출
        List<Map<String, List<String>>> crfData = analyzeFolderData(folderPath, diseaseClass, institutionId);

        // JSON 데이터 추출
        String jsonFolderPath = "C:/app/error_json"; // JSON 파일 저장 경로
        List<Map<String, Object>> jsonData = loadDuplicateDataFromJson(jsonFolderPath, diseaseClass, institutionId);

        // CRF 데이터를 중첩 리스트로 변환
        List<List<String>> crfIds = crfData.stream()
                .flatMap(map -> map.values().stream()) // Map의 값(List<String>) 스트림 생성
                .collect(Collectors.toList()); // 각 값(List<String>)을 리스트에 추가


        // JSON 파일 이름 리스트로 변환
        List<String> jsonFiles = jsonData.stream()
                .flatMap(data -> {
                    Object files = data.get("duplicateFiles");
                    if (files instanceof List) {
                        return ((List<String>) files).stream(); // List<String>을 스트림으로 변환
                    }
                    return Stream.empty();
                })
                .distinct() // 중복 제거
                .collect(Collectors.toList());

        // JSON 오류 파일 예시 처리
        List<String> jsonErrorFiles = jsonFiles.stream()
                .filter(fileName -> fileName.contains("Error")) // 예: 파일 이름에 "Error" 포함
                .collect(Collectors.toList());

        // 결과 구성
        Map<String, Object> result = new HashMap<>();
        result.put("CRF", crfIds);
        result.put("Json", jsonFiles);
        result.put("Json 오류 파일", jsonErrorFiles);

        return result;
    }


}


