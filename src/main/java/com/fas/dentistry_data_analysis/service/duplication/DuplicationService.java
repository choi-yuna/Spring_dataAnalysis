package com.fas.dentistry_data_analysis.service.duplication;

import com.fas.dentistry_data_analysis.service.Json.JSONService;
import com.fas.dentistry_data_analysis.util.ExcelUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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

    public Map<String, Object> analyzeFolderData(String folderPath, String diseaseClass, int institutionId)
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

        // 전체 데이터를 저장할 리스트
        List<Map<String, String>> extractedDataList = Collections.synchronizedList(new ArrayList<>());

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
                processServerFile(file, diseaseClass, institutionId, passIdsSet, combinationMap, duplicateDetailsMap, extractedDataList);
                return null;
            }));
        }

        for (Future<Void> future : futureResults) {
            future.get(); // 각 작업 완료 대기
        }

        executor.shutdown();

        // 중복 데이터를 정리
        Map<String, List<String>> cleanedDuplicateDetails = cleanDuplicateData(duplicateDetailsMap);

        Map<String, Object> result = new HashMap<>();
        result.put("duplicateDetails", cleanedDuplicateDetails); // 정리된 중복 데이터
        result.put("extractedData", extractedDataList);
        return result;
    }


    private boolean isFileMatchingCriteria(String fileName, String diseaseKeyword, String institutionKeyword) {
        boolean matchesDisease = diseaseKeyword.isEmpty() || fileName.contains(diseaseKeyword);
        boolean matchesInstitution = institutionKeyword.isEmpty() || fileName.contains(institutionKeyword);
        return matchesDisease && matchesInstitution;
    }

    public void processServerFile(File file, String diseaseClass, int institutionId, Set<String> passIdsSet,
                                  Map<String, List<String>> combinationMap,
                                  Map<String, List<String>> duplicateDetailsMap,
                                  List<Map<String, String>> extractedDataList) throws IOException {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".xlsx")) {
            findFilteredDuplicateImageIds(file, diseaseClass, institutionId, passIdsSet, combinationMap, duplicateDetailsMap, extractedDataList);
        } else {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
        }
    }

    public void findFilteredDuplicateImageIds(File excelFile, String diseaseClass, int institutionId,
                                              Set<String> passIdsSet,
                                              Map<String, List<String>> combinationMap,
                                              Map<String, List<String>> duplicateDetailsMap,
                                              List<Map<String, String>> extractedDataList) throws IOException {
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


                Integer institutionIdIndex = headerIndexMap.get("INSTITUTION_ID");
                Integer imageIdIndex = headerIndexMap.get("IMAGE_ID");
                Integer captureTimeIndex = headerIndexMap.get("CAPTURE_TIME");
                Integer pAgeIndex = headerIndexMap.get("P_AGE");
                Integer pGenderIndex = headerIndexMap.get("P_GENDER");

                for (int rowIndex = 8; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row != null) {



                        String imageId = ExcelUtils.getCellValueAsString(row.getCell(imageIdIndex)).trim();
                        String institution = ExcelUtils.getCellValueAsString(row.getCell(institutionIdIndex)).trim();
                        String captureTime = ExcelUtils.getCellValueAsString(row.getCell(captureTimeIndex)).trim();
                        String pAge = ExcelUtils.getCellValueAsString(row.getCell(pAgeIndex)).trim();
                        String pGender = ExcelUtils.getCellValueAsString(row.getCell(pGenderIndex)).trim();

                        // 중복 데이터 처리
                        if (!passIdsSet.contains(imageId)) continue;
                        // 전체 데이터를 저장
                        Map<String, String> rowData = new HashMap<>();
                        rowData.put("excelFileName", excelFile.getName());
                        rowData.put("Identifier", imageId);
                        rowData.put("INSTITUTION_ID", institution);
                        rowData.put("CAPTURE_TIME", captureTime);
                        rowData.put("P_AGE", pAge);
                        rowData.put("P_GENDER", pGender);

                        synchronized (extractedDataList) {
                            extractedDataList.add(rowData);
                        }
// 중복 데이터 처리
                        if (!passIdsSet.contains(imageId)) continue;

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
    boolean isValidSheet(Sheet sheet, String fileName) {
        String sheetName = sheet.getSheetName();
        if (fileName.contains("두개안면")) return sheetName.contains("CRF") && sheetName.contains("두개안면기형");
        if (fileName.contains("치주질환")) return sheetName.contains("CRF") && sheetName.contains("치주질환");
        if (fileName.contains("구강암")) return sheetName.contains("CRF") && sheetName.contains("구강암");
        if (fileName.contains("골수염")) return sheetName.contains("CRF") && sheetName.contains("골수염");
        return sheetName.contains("CRF");
    }

    Map<String, Integer> getHeaderIndexMap(Row headerRow) {
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

    void validateHeaderIndices(Map<String, Integer> headerIndexMap) {
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

    public List<Map<String, String>> extractSpecificFieldsFromJson(String jsonFolderPath, String diseaseClass, int institutionId) throws IOException {
        File folder = new File(jsonFolderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("JSON 폴더 경로가 유효하지 않습니다: " + jsonFolderPath);
        }

        // 질환과 기관에 해당하는 키워드 가져오기
        String diseaseKeyword = diseaseClass.equals("0") ? "" : DiseaseClassMap.getOrDefault(diseaseClass, "");
        String institutionKeyword = institutionId == 0 ? "" : InstitutionMap.getOrDefault(String.valueOf(institutionId), "");

        List<Map<String, String>> extractedDataList = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        // JSON 파일 필터링
        File[] jsonFiles = folder.listFiles((dir, name) -> {
            boolean matchesDisease = diseaseKeyword.isEmpty() || name.contains(diseaseKeyword);
            boolean matchesInstitution = institutionKeyword.isEmpty() || name.contains(institutionKeyword);
            return name.endsWith(".json") && matchesDisease && matchesInstitution;
        });

        if (jsonFiles == null || jsonFiles.length == 0) {
            log.warn("JSON 파일이 없습니다: {}", jsonFolderPath);
            return extractedDataList;
        }

        // JSON 파일 읽기 및 필드 추출
        for (File jsonFile : jsonFiles) {
            try (InputStream inputStream = new FileInputStream(jsonFile)) {
                JsonNode rootNode = objectMapper.readTree(inputStream);

                // JSON 배열인지 확인
                if (!rootNode.isArray()) {
                    log.warn("JSON 파일이 배열 형식이어야 합니다: {}", jsonFile.getName());
                    continue;
                }

                for (JsonNode recordNode : rootNode) {
                    Map<String, String> extractedFields = new LinkedHashMap<>();

                    // 필수 필드 추출
                    extractedFields.put("excelFileName", Optional.ofNullable(findValueInSections(recordNode, "excelFileName"))
                            .map(JsonNode::asText).orElse("N/A"));

                    extractedFields.put("INSTITUTION_ID", Optional.ofNullable(findValueInSections(recordNode, "INSTITUTION_ID"))
                            .map(JsonNode::asText).orElse("N/A"));

                    // CAPTURE_TIME 추출 (Image_info 배열의 첫 번째 요소 탐색)
                    extractedFields.put("CAPTURE_TIME", Optional.ofNullable(findValueInSections(recordNode, "CAPTURE_TIME"))
                            .map(JsonNode::asText).orElse("N/A"));

                    // Identifier 또는 Image_id
                    JsonNode identifierNode = findValueInSections(recordNode, "Identifier");
                    JsonNode imageIdNode = findValueInSections(recordNode, "Image_id");
                    extractedFields.put("Identifier", identifierNode != null ? identifierNode.asText("N/A")
                            : (imageIdNode != null ? imageIdNode.asText("N/A") : "N/A"));

                    // Patient_info에서 P_GENDER 및 P_AGE 추출
                    extractedFields.put("P_GENDER", Optional.ofNullable(findValueInSections(recordNode, "P_GENDER"))
                            .map(JsonNode::asText).orElse("N/A"));
                    extractedFields.put("P_AGE", Optional.ofNullable(findValueInSections(recordNode, "P_AGE"))
                            .map(JsonNode::asText).orElse("N/A"));

                    // 추출된 데이터를 리스트에 추가
                    extractedDataList.add(extractedFields);
                }
            } catch (IOException e) {
                log.error("JSON 파일 처리 중 오류 발생: {}", jsonFile.getName(), e);
            }
        }

        return extractedDataList;
    }


    public List<String> compareExcelAndJsonData(
            List<Map<String, String>> excelData,
            List<Map<String, String>> jsonData
    ) {
        // 오류 ID를 저장할 리스트
        List<String> mismatchedIds = new ArrayList<>();

        // JSON 데이터를 Identifier 기준으로 Map에 저장 (중복 키 처리)
        Map<String, Map<String, String>> jsonDataById = jsonData.stream()
                .collect(Collectors.toMap(
                        data -> data.get("Identifier"), // Identifier를 Key로 사용
                        data -> data,                  // Value로 Map 저장
                        (existing, replacement) -> existing // 중복 발생 시 기존 값 유지
                ));

        for (Map<String, String> excelRow : excelData) {
            String id = excelRow.get("Identifier"); // 엑셀의 Identifier 값

            // JSON 데이터에서 동일 ID 찾기
            Map<String, String> jsonRow = jsonDataById.get(id);

            if (jsonRow == null) {
                log.warn("JSON 데이터에서 Identifier를 찾을 수 없습니다: {}", id);
                continue; // JSON 데이터에 해당 ID가 없는 경우 무시
            }

            // 비교할 필드 목록
            List<String> fieldsToCompare = List.of("excelFileName", "CAPTURE_TIME", "P_GENDER", "P_AGE", "INSTITUTION_ID");

            // 필드 값 비교
            boolean hasMismatch = false;
            for (String field : fieldsToCompare) {
                String excelValue = excelRow.get(field);
                String jsonValue = jsonRow.get(field);

                if (!Objects.equals(excelValue, jsonValue)) {
                    log.warn("Mismatch detected for ID {}: Field {} (Excel: {}, JSON: {})",
                            id, field, excelValue, jsonValue);
                    hasMismatch = true;
                }
            }

            // 값이 하나라도 다르면 오류 리스트에 추가
            if (hasMismatch) {
                mismatchedIds.add(id);
            }
        }

        return mismatchedIds; // 오류 Identifier 리스트 반환
    }


    private JsonNode findValueInSections(JsonNode recordNode, String key) {
        // 최상위에서 값 검색
        if (recordNode.has(key)) {
            return recordNode.get(key);
        }

        // JSON 섹션에서 값 검색
        Iterator<Map.Entry<String, JsonNode>> fields = recordNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode section = field.getValue();

            if (section.isArray()) {
                for (JsonNode item : section) {
                    if (item.has(key)) {
                        return item.get(key);
                    }
                }
            } else if (section.has(key)) {
                return section.get(key);
            }
        }

        // 값을 찾을 수 없으면 null 반환
        return null;
    }

    public Map<String, List<String>> cleanDuplicateData(Map<String, List<String>> duplicateDetailsMap) {
        Map<String, List<String>> cleanedData = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : duplicateDetailsMap.entrySet()) {
            String key = entry.getKey();
            List<String> uniqueIds = entry.getValue().stream().distinct().collect(Collectors.toList());
            cleanedData.put(key, uniqueIds);
        }

        return cleanedData;
    }

    public Map<String, Object> extractAndCombineData(String folderPath, String diseaseClass, int institutionId)
            throws IOException, InterruptedException, ExecutionException {
        // 중복된 CRF 데이터 및 전체 데이터 추출
        Map<String, Object> analysisResult = analyzeFolderData(folderPath, diseaseClass, institutionId);

        // 중복 데이터 (duplicateDetails)
        Map<String, List<String>> crfDuplicateData = (Map<String, List<String>>) analysisResult.get("duplicateDetails");

        // 전체 데이터 (extractedData)
        List<Map<String, String>> crfExtractedData = (List<Map<String, String>>) analysisResult.get("extractedData");

        // JSON 데이터 추출
        String jsonFolderPath = "C:/app/error_json"; // JSON 파일 저장 경로
        List<Map<String, Object>> jsonData = loadDuplicateDataFromJson(jsonFolderPath, diseaseClass, institutionId);
        String jsonFolder = "C:/app/disease_json";
        // JSON에서 필요한 필드만 추출
        List<Map<String, String>> jsonSpecificData = extractSpecificFieldsFromJson(jsonFolder, diseaseClass, institutionId);

        List<String> errorJson = compareExcelAndJsonData(crfExtractedData, jsonSpecificData);

        // CRF 중복 데이터를 리스트로 변환
        List<List<String>> crfIds = crfDuplicateData.values().stream()
                .collect(Collectors.toList());

        // JSON 파일 이름 리스트로 변환
        List<String> jsonFiles = jsonData.stream()
                .flatMap(data -> {
                    Object files = data.get("duplicateFiles");
                    if (files instanceof List) {
                        return ((List<String>) files).stream();
                    }
                    return Stream.empty();
                })
                .distinct() // 중복 제거
                .collect(Collectors.toList());

        // 결과 구성
        Map<String, Object> result = new HashMap<>();
        result.put("CRF", crfIds);
        result.put("JSON ", jsonFiles);
        result.put("JSON 오류 파일", errorJson);

        return result;
    }




}


