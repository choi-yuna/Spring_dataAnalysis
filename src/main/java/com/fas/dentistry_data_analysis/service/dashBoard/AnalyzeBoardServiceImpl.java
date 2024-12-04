package com.fas.dentistry_data_analysis.service.dashBoard;

import com.fas.dentistry_data_analysis.util.SFTPClient;
import com.fas.dentistry_data_analysis.util.ValueMapping;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnalyzeBoardServiceImpl {
    //원광대 서버 정보
    private static final String SFTP_HOST = "210.126.75.11";  // SFTP 서버 IP
    private static final int SFTP_PORT = 2024;  // SFTP 포트
    private static final String SFTP_USER = "master01";  // 사용자 계정
    private static final String SFTP_PASSWORD = "Master01!!!";  // 비밀번호
    // SFTP 서버 정보
//    private static final String SFTP_HOST = "202.86.11.27";  // SFTP 서버 IP
//    private static final int SFTP_PORT = 22;  // SFTP 포트
//    private static final String SFTP_USER = "dent_fas";  // 사용자 계정
//    private static final String SFTP_PASSWORD = "dent_fas123";  // 비밀번호

    private final DataGropedService dataGropedService;
    private final ExcelService excelService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2 * Runtime.getRuntime().availableProcessors());

    public AnalyzeBoardServiceImpl(DataGropedService dataGropedService, ExcelService excelService) {
        this.dataGropedService = dataGropedService;
        this.excelService = excelService;
    }

    public Map<String, Object> processFilesInFolder(String folderPath, boolean refresh) throws Exception {
        folderFileCache.clear();

        List<Map<String, Object>> resultList = new ArrayList<>();


        Session session = null;
        ChannelSftp channelSftp = null;
        Set<String> processedImageIds = new HashSet<>();  // 중복 처리용 전역 Set

        try {
            session = SFTPClient.createSession(SFTP_HOST, SFTP_USER, SFTP_PASSWORD, SFTP_PORT);
            channelSftp = SFTPClient.createSftpChannel(session);

            // 폴더 내 파일을 병렬로 처리
            processFolderRecursively(channelSftp, folderPath, resultList, processedImageIds, refresh);

        } finally {
            if (channelSftp != null) channelSftp.disconnect();
            if (session != null) session.disconnect();
            log.info("SFTP connection closed");
        }

        Map<String, Object> response = new HashMap<>();

        // 질환별 데이터 그룹화
        List<Map<String, Object>> diseaseData = new ArrayList<>();
        diseaseData.add(dataGropedService.createDiseaseData(resultList, "INSTITUTION_ID", "질환 ALL"));
        diseaseData.addAll(dataGropedService.groupDataByDisease(resultList));  // 그룹화된 데이터 추가
        response.put("질환별", diseaseData);

        // 기관별 데이터 그룹화
        List<Map<String, Object>> institutionData = new ArrayList<>();
        institutionData.add(dataGropedService.createInstitutionData(resultList, "DISEASE_CLASS", "기관 ALL"));
        institutionData.addAll(dataGropedService.groupDataByInstitution(resultList));  // 그룹화된 데이터 추가
        response.put("기관별", institutionData);

        // 중복 체크
        Set<String> uniqueImageIds = new HashSet<>();
        int nullCount = 0;

        for (Map<String, Object> row : resultList) {
            String imageId = (String) row.get("IMAGE_ID");

            if (imageId == null) {
                nullCount++;
            } else {
                uniqueImageIds.add(imageId);
            }
        }

        log.info("Total number of unique image IDs: {}", uniqueImageIds.size());
        log.info("Number of null image IDs: {}", nullCount);

        return response;
    }

    private void processFolderRecursively(ChannelSftp channelSftp, String folderPath, List<Map<String, Object>> resultList, Set<String> processedImageIds, boolean refresh) throws Exception {
        Vector<ChannelSftp.LsEntry> files;

        try {
            // 디렉토리 파일 목록 가져오기
            files = SFTPClient.listFiles(channelSftp, folderPath);
        } catch (SftpException e) {
            if (e.getMessage().contains("Permission denied")) {
                log.warn("Permission denied for folder: {}. Skipping this folder.", folderPath);
                return; // 권한 문제가 있는 폴더는 건너뛰기
            }
            throw e; // 다른 예외는 재발생
        }

        log.info("Found {} files in folder: {}", files.size(), folderPath);

        // JSON 파일 존재 여부와 refresh 파라미터에 따라 처리
        String jsonFilePath = folderPath + "/analysis_result.json";
        if (checkFileExistsInSFTP(channelSftp, folderPath, "analysis_result.json", "")) {
            if (refresh) {
                try {
                    SFTPClient.deleteFile(channelSftp, jsonFilePath);
                    log.info("Existing analysis_result.json deleted for folder: {}", folderPath);
                } catch (SftpException e) {
                    if (e.getMessage().contains("No such file")) {
                        log.warn("No existing analysis_result.json to delete for folder: {}", folderPath);
                    } else {
                        throw e; // 다른 예외는 재발생
                    }
                }
            } else {
                log.info("JSON result file already exists for folder: {}", folderPath);
                List<Map<String, Object>> existingResults = loadResultsFromJsonSftp(folderPath, channelSftp);
                resultList.addAll(existingResults);
                return; // 추가 처리 건너뜁니다.
            }
        }

        // 결과를 새로 분석하는 로직
        List<Map<String, Object>> folderResultList = new ArrayList<>();
        boolean isExcelFileProcessed = false;

        int availableCores = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = Executors.newFixedThreadPool(availableCores);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicBoolean stopSubfolderSearch = new AtomicBoolean(false);

        for (ChannelSftp.LsEntry entry : files) {
            String fileName = entry.getFilename();
            if (fileName.endsWith(".xlsx")) {
                synchronized (processedImageIds) {
                    processedImageIds.add(fileName);
                }

                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        processFile(channelSftp, folderPath, fileName, folderResultList, processedImageIds, stopSubfolderSearch);
                    } catch (Exception e) {
                        log.error("Error processing file: {}", fileName, e);
                    }
                }, executorService));

                isExcelFileProcessed = true;
                stopSubfolderSearch.set(true);
                break;
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        resultList.addAll(folderResultList);

        // JSON 파일로 결과 저장
        if (isExcelFileProcessed) {
            saveResultsToJsonSftp(folderPath, folderResultList, channelSftp);
            log.info("Processed and saved results to JSON for folder: {}", folderPath);
        }

        // 하위 폴더 탐색 진행
        if (!stopSubfolderSearch.get()) {
            for (ChannelSftp.LsEntry entry : files) {
                if (entry.getAttrs().isDir() && !entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    String subFolderPath = folderPath + "/" + entry.getFilename();
                    if (subFolderPath.contains("/Labelling/Labelling")) {
                        continue;
                    }
                    processFolderRecursively(channelSftp, subFolderPath, resultList, processedImageIds, refresh);
                }
            }
        }

        executorService.shutdown();
    }


    private List<Map<String, Object>> loadResultsFromJsonSftp(String folderPath, ChannelSftp channelSftp) throws IOException, SftpException {
        // JSON 파일 경로
        String jsonFilePath = folderPath + "/analysis_result.json";

        // JSON 파일 로드
        try (InputStream inputStream = SFTPClient.readFile(channelSftp, folderPath, "analysis_result.json")) {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {});
        }
    }


    private void saveResultsToJsonSftp(String folderPath, List<Map<String, Object>> resultList, ChannelSftp channelSftp) throws IOException, SftpException {
        // 결과를 JSON 형식으로 변환
        List<Map<String, Object>> jsonResultList = new ArrayList<>();

        for (Map<String, Object> result : resultList) {
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("DISEASE_CLASS", result.getOrDefault("DISEASE_CLASS", "N/A"));
            resultData.put("INSTITUTION_ID", result.getOrDefault("INSTITUTION_ID", "N/A"));
            resultData.put("라벨링등록건수", result.getOrDefault("라벨링등록건수", 0));
            resultData.put("라벨링pass건수", result.getOrDefault("라벨링pass건수", 0));
            resultData.put("1차검수", result.getOrDefault("1차검수", 0));
            resultData.put("2차검수", result.getOrDefault("2차검수", 0));
            jsonResultList.add(resultData);
        }

        // ObjectMapper를 사용하여 JSON 형식으로 변환
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonContent = objectMapper.writeValueAsString(jsonResultList);

        // 문자열 데이터를 InputStream으로 변환
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());

        // SFTP 서버에 저장 (폴더 경로 + 파일 이름 지정)
        String sftpFilePath = folderPath + "/analysis_result.json";

        SFTPClient.uploadFile(channelSftp, folderPath, "analysis_result.json", inputStream);

        log.info("Results successfully saved to SFTP at: {}", sftpFilePath);
    }


    private void processJsonInputStream(InputStream jsonInputStream, List<Map<String, Object>> resultList, String institutionId, String diseaseClass, String imageId) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonInputStream);

        // Annotation_Data 확인 로직
        JsonNode annotationData = rootNode.get("Annotation_Data");
        boolean allKeysExist = false;

        if (annotationData != null && annotationData.isArray() && annotationData.size() > 0) {
            JsonNode firstAnnotation = annotationData.get(0); // 첫 번째 Annotation_Data만 검사
            List<String> requiredKeys = Arrays.asList(
                    "LM_S", "LM_N", "LM_POR", "LM_POL", "LM_ORR", "LM_ORL",
                    "LM_ARR", "LM_ARL", "LM_ANS", "LM_PNS", "LM_A", "LM_SPR",
                    "LM_ID", "LM_B", "LM_POG", "LM_GN", "LM_ME", "LM_GOR",
                    "LM_GOL", "LM_COR", "LM_COL", "LM_U1R", "LM_U1L", "LM_U1AR",
                    "LM_U1AL", "LM_L1R", "LM_L1L", "LM_L1AR", "LM_L1AL", "LM_U3R",
                    "LM_U3L", "LM_L3R", "LM_L3L", "LM_U6R", "LM_U6L", "LM_UA6R",
                    "LM_UA6L", "LM_L6R", "LM_L6L", "LM_LA6R", "LM_LA6L", "LM_FZPR",
                    "LM_FZPL", "LM_MR", "LM_ML"
            );

            // 모든 키가 존재하는지 확인
            allKeysExist = requiredKeys.stream().allMatch(firstAnnotation::has);
        }

        // 상태 업데이트
        if (allKeysExist) {
            incrementStatus(resultList, institutionId, diseaseClass, imageId, "라벨링pass건수",null);
        }
    }


    //질환별 폴더 확인 로직
    private void processFile(ChannelSftp channelSftp, String folderPath, String fileName,
                             List<Map<String, Object>> resultList, Set<String> processedImageIds,
                             AtomicBoolean stopSubfolderSearch) throws Exception {

        // 엑셀 파일 처리 (엑셀 파일에는 DISEASE_CLASS와 INSTITUTION_ID가 없다)
        InputStream inputStream = SFTPClient.readFile(channelSftp, folderPath, fileName);
        List<Map<String, Object>> filteredData = excelService.processExcelFile(inputStream);

        // JSON 파일 경로 확인
        String jsonPath = folderPath.contains("치주질환") ? folderPath + "/Labelling/meta" : folderPath + "/Labelling";
        Set<String> jsonFiles = folderFileCache.computeIfAbsent(jsonPath, path -> {
            try {
                Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, path);
                return files.stream()
                        .map(ChannelSftp.LsEntry::getFilename)
                        .filter(name -> name.endsWith(".json"))
                        .collect(Collectors.toSet());
            } catch (SftpException e) {
                return Collections.emptySet();
            }
        });

        // 엑셀 데이터에서 IMAGE_ID만 추출 (엑셀에서 DISEASE_CLASS, INSTITUTION_ID는 제외)
        Set<String> imageIdsFromExcel = filteredData.stream()
                .map(row -> (String) row.get("IMAGE_ID"))
                .collect(Collectors.toSet());

        // JSON 파일에서 DISEASE_CLASS와 INSTITUTION_ID 추출
        String diseaseClass = null;
        String institutionId = null;

//        // JSON 파일에서 질환과 기관 정보를 먼저 추출
//        for (String jsonFileName : jsonFiles) {
//            Map<String, String> jsonData = extractInstitutionAndDiseaseFromJson(channelSftp, jsonPath, jsonFileName);
//            if (jsonData.get("DISEASE_CLASS") != null) {
//                String JsonDiseaseClass = jsonData.get("DISEASE_CLASS");
//                diseaseClass = ValueMapping.getDiseaseClass(JsonDiseaseClass);
//            }
//            if (jsonData.get("INSTITUTION_ID") != null) {
//                String jsonInstitutionId = jsonData.get("INSTITUTION_ID");
//                institutionId = ValueMapping.getInstitutionDescription(jsonInstitutionId);
//            }
//            if (diseaseClass != null && institutionId != null) break;  // 값이 모두 추출되면 종료
//        }
//
//        if (diseaseClass == null || institutionId == null) {
//            log.warn("Unable to determine DISEASE_CLASS or INSTITUTION_ID for file: {}", fileName);
//            return;  // 필수 데이터가 없으면 중단
//        }
// 폴더명을 분석하여 DISEASE_CLASS와 INSTITUTION_ID 추출
        if (folderPath.contains("치주질환")) {
            diseaseClass = "치주질환";
        } else if (folderPath.contains("두개안면")) {
            diseaseClass = "두개안면";
        } else if (folderPath.contains("구강암")) {
            diseaseClass = "구강암";
        }
        else if (folderPath.contains("골수염")) {
            diseaseClass = "골수염";
        }
        else if (folderPath.contains("대조군")) {
            diseaseClass = "대조군";
        }else {
            log.warn("Unknown disease class in folder path: {}", folderPath);
        }

        if (folderPath.contains("고려대")) {
            institutionId = "고려대학교";
        } else if (folderPath.contains("보라매")) {
            institutionId = "보라매병원";
        } else if (folderPath.contains("단국대")) {
            institutionId = "단국대학교";
        } else if (folderPath.contains("국립암센터")) {
            institutionId = "국립암센터";
        }else if (folderPath.contains("서울대")) {
            institutionId = "서울대학교";
        }else if (folderPath.contains("원광대")) {
            institutionId = "원광대학교";}
        else if (folderPath.contains("조선대")) {
                institutionId = "조선대학교";
        }else {
            log.warn("Unknown institution in folder path: {}", folderPath);
        }

// DISEASE_CLASS와 INSTITUTION_ID가 모두 추출되지 않았을 경우 처리
        if (diseaseClass == null || institutionId == null) {
            log.warn("Unable to determine DISEASE_CLASS or INSTITUTION_ID for file: {}", fileName);
            return;  // 필수 데이터가 없으면 중단
        }


        // JSON 파일 개수로 라벨링등록건수 설정
        if (!jsonFiles.isEmpty()) {
            incrementStatus(resultList, institutionId, diseaseClass, null, "라벨링등록건수", jsonFiles.size());
        }

        // 엑셀 파일에서 추출된 IMAGE_ID와 JSON에서 얻은 DISEASE_CLASS, INSTITUTION_ID를 매핑하여 처리
        for (Map<String, Object> row : filteredData) {
            String imageId = (String) row.get("IMAGE_ID");

            // 엑셀 파일에서 IMAGE_ID가 JSON 파일에 포함된 경우 처리
            if (imageIdsFromExcel.contains(imageId)) {
                boolean jsonExists = jsonFiles.stream().anyMatch(name -> name.contains(imageId));
                if (!jsonExists) continue;
            }

            // 파일 존재 여부를 확인하는 부분 (치주질환 폴더 확인)
            boolean dcmExists = false;
            boolean jsonExists = false;
            boolean iniExists = false;
            boolean toothExists = false;
            boolean tlaExists = false;
            boolean cejExists = false;
            boolean alveExists = false;
            boolean labellingExists = false;


            if (folderPath.contains("치주질환")) {
                dcmExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".dcm", "");
                jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling/meta");
                iniExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");
                toothExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".png", "/Labelling/tooth");
                tlaExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".png", "/Labelling/tla");
                cejExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".png", "/Labelling/cej");
                alveExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".png", "/Labelling/alve");
                if(jsonExists) {
                    if ((dcmExists && iniExists && toothExists && tlaExists && cejExists && alveExists)) {
                        incrementStatus(resultList, institutionId, diseaseClass, imageId, "라벨링pass건수",null);
                        processJsonFile(channelSftp, folderPath, imageId, resultList, institutionId, diseaseClass);
                        stopSubfolderSearch.set(true);  // 이 시점에서 하위 폴더 탐색을 중지
                    } else {

                        stopSubfolderSearch.set(true);  // 이 시점에서 하위 폴더 탐색을 중지
                    }
                }
            }
            else if (folderPath.contains("두개안면")) {
                jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling");
                if (jsonExists) {
                    try (InputStream jsonInputStream = SFTPClient.readFile(channelSftp, folderPath+"/Labelling", imageId + ".json")) {
                        // JSON 데이터 처리
                        processJsonInputStream(jsonInputStream, resultList, institutionId, diseaseClass, imageId);
                    } catch (Exception e) {
                        log.error("Error processing JSON file for Image ID: {}", imageId, e);
                    }
                }
            }

            else {
                dcmExists = checkFileExistsInSFTPForImageId(channelSftp, folderPath, imageId);
                jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling");
                iniExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");
                labellingExists = checkLabellingFileExistsInSFTPForImageId(channelSftp,folderPath+"/Labelling/Labelling", imageId);
                if (jsonExists) {
                    if ((dcmExists && labellingExists && iniExists)) {
                        incrementStatus(resultList, institutionId, diseaseClass, imageId, "라벨링pass건수",null);
                        processJsonFile(channelSftp, folderPath, imageId, resultList, institutionId, diseaseClass);
                        stopSubfolderSearch.set(true);  // 이 시점에서 하위 폴더 탐색을 중지
                    } else {
                        stopSubfolderSearch.set(true);  // 이 시점에서 하위 폴더 탐색을 중지
                    }
                }
            }

            // 파일 존재 여부에 따라 상태 업데이트

        }

    }
    private boolean checkFileExistsInSFTPForImageId(ChannelSftp channelSftp, String folderPath, String imageId) throws SftpException {
        // 주어진 경로에 대한 캐시를 초기화하거나 가져옴
        Set<String> cachedFolders = folderFileCache.computeIfAbsent(folderPath, path -> {
            try {
                Vector<ChannelSftp.LsEntry> entries = SFTPClient.listFiles(channelSftp, path);
                Set<String> folderNames = new HashSet<>();
                for (ChannelSftp.LsEntry entry : entries) {
                    if (entry.getAttrs().isDir()) { // 디렉토리만 추가
                        folderNames.add(entry.getFilename());
                    }
                }
                return folderNames;
            } catch (SftpException e) {
                // 폴더 목록을 가져오는 데 실패한 경우
                return Collections.emptySet();
            }
        });

        // 폴더 이름 중 imageId를 포함하는 폴더 찾기
        Optional<String> matchingFolder = cachedFolders.stream()
                .filter(folderName -> folderName.contains(imageId))
                .findFirst();

        if (matchingFolder.isPresent()) {
            String targetFolderPath = folderPath + "/" + matchingFolder.get();

            // 해당 폴더 내 파일 캐시 초기화 또는 가져오기
            Set<String> cachedFiles = folderFileCache.computeIfAbsent(targetFolderPath, path -> {
                try {
                    Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, path);
                    Set<String> fileNames = new HashSet<>();
                    for (ChannelSftp.LsEntry entry : files) {
                        fileNames.add(entry.getFilename());
                    }
                    return fileNames;
                } catch (SftpException e) {
                    // 파일 목록을 가져오는 데 실패한 경우
                    return Collections.emptySet();
                }
            });

            // .dcm 파일이 존재하는지 확인
            return cachedFiles.stream().anyMatch(fileName -> fileName.endsWith(".dcm"));
        }

        return false;
    }


    private boolean checkLabellingFileExistsInSFTPForImageId(ChannelSftp channelSftp, String folderPath, String imageId) {
        // 캐시에서 파일/폴더 세트를 가져오거나 초기화
        Set<String> filesInCache = folderFileCache.computeIfAbsent(folderPath, path -> {
            try {
                Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, path);
                return files.stream()
                        .map(ChannelSftp.LsEntry::getFilename)
                        .collect(Collectors.toSet());
            } catch (SftpException e) {
                log.error("Failed to list files in folder: {}", path, e);
                return Collections.emptySet();
            }
        });

        // 캐시에서 imageId를 포함하는 파일/폴더 이름이 있는지 확인
        return filesInCache.stream().anyMatch(name -> name.contains(imageId));
    }



    /**
     *JSON에서 검수 값 읽어오기
     */
    private void processJsonFile(ChannelSftp channelSftp, String folderPath, String imageId, List<Map<String, Object>> resultList, String institutionId, String diseaseClass) throws Exception {

        // JSON 파일 경로 설정
        String jsonFilePath = folderPath + (folderPath.contains("치주질환") ? "/Labelling/meta/" : "/Labelling/");
//        String labelingKey = folderPath.contains("치주질환") ? "Labeling_Info" : "Labeling_info";
//        String firstCheckKey = folderPath.contains("치주질환") ? "First_Check_Info" : "First_Check_info";
        String secondCheckKey = folderPath.contains("치주질환") ? "Second_Check_Info" : "Second_Check_info";


        try (InputStream jsonFileStream = SFTPClient.readFile(channelSftp, jsonFilePath, imageId + ".json")) {

            // ObjectMapper 사용하여 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonFileStream);


            // 상태 추출
//            boolean labelingStatus = getJsonStatus(rootNode, labelingKey) == 2;
//            boolean firstCheckStatus = getJsonStatus(rootNode, firstCheckKey) == 2;
            boolean secondCheckStatus = getJsonStatus(rootNode, secondCheckKey) == 2;


            // 상태가 2인 경우에만 처리
            //라벨링 건수 로직
//            if (labelingStatus) {
//                incrementStatus(resultList, institutionId, diseaseClass, imageId, "라벨링건수");
//            }
//            if (firstCheckStatus) {
//                incrementStatus(resultList, institutionId, diseaseClass, imageId, "1차검수");
//            }
            if (secondCheckStatus) {
                incrementStatus(resultList, institutionId, diseaseClass, imageId, "2차검수",null);
            }
        } catch (Exception e) {
            log.error("Error while processing JSON file for Image ID: {}", imageId, e);
        }
    }
    private Map<String, String> extractInstitutionAndDiseaseFromJson(ChannelSftp channelSftp, String folderPath, String jsonFileName) throws Exception {
        // JSON 파일 읽기
        try (InputStream inputStream = SFTPClient.readFile(channelSftp, folderPath, jsonFileName)) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(inputStream);

            // DISEASE_CLASS와 INSTITUTION_ID 추출
            String diseaseClass = rootNode.path("DISEASE_CLASS").asText(null);
            String institutionId = rootNode.path("INSTITUTION_ID").asText(null);

            // 결과를 Map으로 반환
            Map<String, String> result = new HashMap<>();
            result.put("DISEASE_CLASS", diseaseClass);
            result.put("INSTITUTION_ID", institutionId);

            return result;
        } catch (Exception e) {
            log.error("Error reading JSON file {}: {}", jsonFileName, e.getMessage());
            return Collections.emptyMap(); // 읽기 실패 시 빈 Map 반환
        }
    }


    // 중복된 IMAGE_ID를 처리하는 방식 개선
    private void incrementStatus(List<Map<String, Object>> resultList, String institutionId, String diseaseClass,
                                 String imageId, String status, Integer incrementValue) {
        // INSTITUTION_ID와 DISEASE_CLASS를 기준으로 항목을 찾습니다.
        Optional<Map<String, Object>> existing = resultList.stream()
                .filter(item -> institutionId.equals(item.get("INSTITUTION_ID"))
                        && diseaseClass.equals(item.get("DISEASE_CLASS")))  // DISEASE_CLASS를 기준으로 정확히 필터링
                .findFirst();

        // 해당 항목이 없다면 새로 추가합니다.
        if (existing.isEmpty()) {
            Map<String, Object> newEntry = new HashMap<>();
            newEntry.put("INSTITUTION_ID", institutionId);
            newEntry.put("DISEASE_CLASS", diseaseClass);  // DISEASE_CLASS 추가
            newEntry.put("목표건수", 0);  // 목표건수는 초기화
            newEntry.put("라벨링등록건수", 0);
            newEntry.put("라벨링pass건수", 0);
            newEntry.put("1차검수", 0);
            newEntry.put("데이터구성검수", 0);
            newEntry.put("2차검수", 0);
            resultList.add(newEntry);
            existing = Optional.of(newEntry);
        }

        Map<String, Object> statusMap = existing.get();

        // 상태 값 증가 (incrementValue가 null이면 기본 1 증가)
        int increment = (incrementValue != null) ? incrementValue : 1;
        Integer currentStatusValue = (Integer) statusMap.get(status);
        if (currentStatusValue == null) {
            currentStatusValue = 0;
        }
        statusMap.put(status, currentStatusValue + increment);
    }


    private final Map<String, Set<String>> folderFileCache = new ConcurrentHashMap<>();

    private boolean checkFileExistsInSFTP(ChannelSftp channelSftp, String folderPath, String fileName, String subFolder) throws SftpException {
        String targetPath = folderPath + subFolder;

        Set<String> cachedFiles = folderFileCache.computeIfAbsent(targetPath, path -> {
            try {
                Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, targetPath);
                Set<String> fileNames = new HashSet<>();
                for (ChannelSftp.LsEntry entry : files) {
                    fileNames.add(entry.getFilename());
                }
                return fileNames;
            } catch (SftpException e) {
                // 파일 목록을 가져오는 데 실패한 경우
                return Collections.emptySet();
            }
        });

        return cachedFiles.contains(fileName);
    }







    // JOSN 상태확인 키 확인
    private int getJsonStatus(JsonNode rootNode, String key) {
        // 각 key에 대한 변형된 키들을 처리
        String[] possibleKeys = getPossibleKeysForKey(key);

        // 각 key 이름 변형에 대해 한 번만 처리
        for (String possibleKey : possibleKeys) {
            JsonNode infoNode = rootNode.get(possibleKey);
            if (infoNode != null && infoNode.isArray() && infoNode.size() > 0) {
                JsonNode firstElement = infoNode.get(0);

                // 상태 확인을 위한 키들
                JsonNode statusNode = getField(firstElement, "Labelling", "Labeling");
                JsonNode firstCheckNode = getField(firstElement, "Checking1", "Checking_1");
                JsonNode secondCheckNode = getField(firstElement, "Checking2", "Checking_2");

                // Labeling 상태 확인 (statusNode가 null이면 0 반환)
                if (statusNode != null && (statusNode.asText().equals("2") || statusNode.asText().equals("완료"))) {
                    return 2;
                }

                // First_Check 상태 확인 (firstCheckNode가 null이면 0 반환)
                if (firstCheckNode != null && firstCheckNode.asText().equals("2")) {
                    return 2;
                }

                // Second_Check 상태 확인 (secondCheckNode가 null이면 0 반환)
                if (secondCheckNode != null && secondCheckNode.asText().equals("2")) {
                    return 2;
                }
            }
        }

        return 0; // 모든 조건을 만족하지 않으면 0 반환
    }

    // key에 따라 두 가지 필드를 반환하는 메서드
    private JsonNode getField(JsonNode node, String key1, String key2) {
        JsonNode fieldNode = node.get(key1);
        if (fieldNode == null) {
            fieldNode = node.get(key2); // 두 번째 키가 있으면 반환
        }
        return fieldNode; // 첫 번째 키가 없으면 두 번째 키를 반환
    }


    private String[] getPossibleKeysForKey(String key) {
        // 각 key에 대해 가능한 이름 변형을 반환
        switch (key) {
            case "Labeling_Info":
                return new String[]{"Labeling_Info", "Labeling_info"};
            case "First_Check_Info":
                return new String[]{"First_Check_Info", "First_Check_info"};
            case "Second_Check_Info":
                return new String[]{"Second_Check_Info", "Second_Check_info"};
            default:
                return new String[]{key};  // 기본적으로 동일한 이름 사용
        }
    }

}