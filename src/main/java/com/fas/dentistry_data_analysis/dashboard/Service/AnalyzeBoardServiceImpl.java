package com.fas.dentistry_data_analysis.dashboard.Service;

import com.fas.dentistry_data_analysis.config.SftpConfig;
import com.fas.dentistry_data_analysis.config.StorageConfig;
import com.fas.dentistry_data_analysis.dataAnlaysis.service.FolderFileCacheManager;
import com.fas.dentistry_data_analysis.common.service.JSONService;
import com.fas.dentistry_data_analysis.common.util.sftp.SFTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private final JSONService jsonService;
    private final SftpService  sftpService;
    private final TotalDataGropedService totalDataGropedService;
    private final ExcelService excelService;
    private final FolderFileCacheManager folderFileCacheManager;
    private final StorageConfig storageConfig;
    private final SftpConfig sftpConfig;


    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    private static final List<String> INSTITUTION_FOLDER_NAMES = Arrays.asList("서울대", "보라매병원", "조선대","원광대","단국대","고려대","국립암센터");
    // 기관-질환별 JSON 파일 목록 관리
    private final Map<String, Set<String>> institutionDiseaseJsonFiles = new HashMap<>();


    public AnalyzeBoardServiceImpl(SftpService sftpService,JSONService jsonService, ExcelService excelService, TotalDataGropedService totalDataGropedService,FolderFileCacheManager folderFileCacheManager, StorageConfig storageConfig, SftpConfig sftpConfig) {
        this.jsonService = jsonService;
        this.excelService = excelService;
        this.totalDataGropedService = totalDataGropedService;
        this.folderFileCacheManager = folderFileCacheManager;
        this.storageConfig = storageConfig;
        this.sftpConfig = sftpConfig;
        this.sftpService = sftpService;
    }

    public boolean isRefreshInProgress() {
        return refreshInProgress.get();
    }

    public Map<String, Object> processFilesInFolder(String folderPath, boolean refresh) throws Exception {
        folderFileCacheManager.clearCache();
        if (refresh && !refreshInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("Refresh is already in progress.");
        }


        // 중복 JSON 파일 정보를 저장할 Map
        Map<String, Map<String, List<String>>> duplicateJsonFiles = new HashMap<>();

        List<String> passIds = new ArrayList<>();
        List<Map<String, Object>> resultList = new ArrayList<>();
        List<Map<String, Object>> errorList = new ArrayList<>();

        Session session = null;
        ChannelSftp channelSftp = null;
        Set<String> processedImageIds = new HashSet<>();  // 중복 처리용 전역 Set

        try {

            session = SFTPClient.createSession(sftpConfig.getHost(), sftpConfig.getUser(), sftpConfig.getPassword(), sftpConfig.getPort());
            channelSftp = SFTPClient.createSftpChannel(session);

            if(refresh) {
                jsonService.deleteExistingExcelFiles("C:/app/dentistry",".xlsx");
                jsonService.deleteExistingExcelFiles("C:/app/id",".json");
                jsonService.deleteExistingExcelFiles("C:/app/disease_json", ".json"); // JSON 저장 폴더 초기화
                jsonService.deleteExistingExcelFiles("C:/app/error_json", ".json"); // JSON 저장 폴더 초기화
                SFTPClient.deleteFile(channelSftp, "/내부 데이터/analysis_result.json");
            }

            // 폴더 내 파일을 병렬로 처리
            processFolderRecursively(channelSftp, folderPath, resultList, errorList, processedImageIds, refresh, passIds,duplicateJsonFiles);

        } finally {
            if (refresh) {
                jsonService.savePassIdsToJson(passIds,"C:/app/id");

                // 중복 JSON 파일 정보를 저장
                jsonService.saveDuplicateJsonInfoToLocal(duplicateJsonFiles, "C:/app/error_json");

                refreshInProgress.set(false);
            }
            if (channelSftp != null) channelSftp.disconnect();
            if (session != null) session.disconnect();
            log.info("SFTP connection closed");
        }

        Map<String, Object> response = new HashMap<>();

        List<Map<String, Object>> errorData = new ArrayList<>();
        Set<String> passIdsSet = new HashSet<>(jsonService.loadPassIdsFromJson("C:/app/id/pass_ids.json"));
        errorData.addAll(totalDataGropedService.groupErrorData(resultList,passIdsSet));  // 그룹화된 데이터 추가
        response.put("errorData", errorData);

        // 질환별 데이터 그룹화
        List<Map<String, Object>> totalDiseaseData = new ArrayList<>();
        totalDiseaseData.add(totalDataGropedService.createDiseaseData(resultList, "INSTITUTION_ID", "질환 ALL"));
        totalDiseaseData.addAll(totalDataGropedService.groupDataByDisease(resultList));  // 그룹화된 데이터 추가
        response.put("질환별", totalDiseaseData);

        // 기관별 데이터 그룹화
        List<Map<String, Object>> totalInstitutionData = new ArrayList<>();
        totalInstitutionData.add(totalDataGropedService.createInstitutionData(resultList, "DISEASE_CLASS", "기관 ALL"));
        totalInstitutionData.addAll(totalDataGropedService.groupDataByInstitution(resultList));  // 그룹화된 데이터 추가
        response.put("기관별", totalInstitutionData);

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

        return response;
    }

    private String extractInstitutionId(String folderPath) {
        if (folderPath.contains("고려대")) return "고려대학교";
        if (folderPath.contains("서울대")) return "서울대학교";
        if (folderPath.contains("단국대")) return "단국대학교";
        if (folderPath.contains("보라매")) return "보라매병원";
        if (folderPath.contains("원광대")) return "원광대학교";
        if (folderPath.contains("조선대")) return "조선대학교";
        return null; // 매칭되지 않는 경우
    }

    private String extractDiseaseClass(String folderPath) {
        if (folderPath.contains("치주질환")) return "치주질환";
        if (folderPath.contains("두개안면")) return "두개안면";
        if (folderPath.contains("구강암")) return "구강암";
        if (folderPath.contains("골수염")) return "골수염";
        if (folderPath.contains("대조군")) return "골수염(대조군)";
        return null; // 매칭되지 않는 경우
    }


    private void processFolderRecursively(ChannelSftp channelSftp, String folderPath,
                                          List<Map<String, Object>> resultList,
                                          List<Map<String, Object>> errorList,
                                          Set<String> processedImageIds,
                                          boolean refresh,List<String> passIds,
                                          Map<String, Map<String, List<String>>> duplicateJsonFiles) throws Exception {
        List<ChannelSftp.LsEntry> files;

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



        // 기관 및 질환 정보 추출
        String institutionId = extractInstitutionId(folderPath);
        String diseaseClass = extractDiseaseClass(folderPath);

        // 기관-질환 키 생성
        String institutionDiseaseKey = institutionId + "_" + diseaseClass;

        // 기관-질환별 JSON 파일 목록 초기화
        Map<String, List<String>> diseaseData = duplicateJsonFiles.computeIfAbsent(institutionId, k -> new HashMap<>());
        List<String> duplicates = diseaseData.computeIfAbsent(diseaseClass, k -> new ArrayList<>());

        // JSON 파일 목록 추적
        Set<String> jsonFileSet = institutionDiseaseJsonFiles.computeIfAbsent(institutionDiseaseKey, k -> new HashSet<>());

        // 현재 폴더의 JSON 파일 목록 가져오기
        String jsonPath = folderPath.contains("치주질환") ? folderPath + "/Labelling/meta" : folderPath + "/Labelling";
        Set<String> jsonFiles = folderFileCacheManager.computeIfAbsent(jsonPath, path -> {
            List<ChannelSftp.LsEntry> entries = SFTPClient.listFiles(channelSftp, path);
            return entries.stream()
                    .map(ChannelSftp.LsEntry::getFilename)
                    .filter(name -> name.endsWith(".json"))
                    .collect(Collectors.toSet());
        });

        // 중복 파일 체크 및 추가
        for (String jsonFile : jsonFiles) {
            if (!jsonFileSet.add(jsonFile)) { // 이미 추가된 파일이라면 중복으로 간주
                if (!duplicates.contains(jsonFile)) {
                    duplicates.add(jsonFile); // 중복 목록에 추가
                }
            }
        }

        String targetDiseaseFolder = getTargetInstitutionFolder(folderPath);

        // JSON 파일 존재 여부 확인
        String jsonFilePath = targetDiseaseFolder != null ? targetDiseaseFolder + "/analysis_result.json"
                : folderPath + "/analysis_result.json";

        if (sftpService.checkFileExistsInSFTP(channelSftp, folderPath, "analysis_result.json", "")) {
            if (refresh) {
                folderFileCacheManager.clearCache();
                try {
                    SFTPClient.deleteFile(channelSftp, jsonFilePath);
                    log.info("Existing analysis_result.json deleted for folder: {}", jsonFilePath);
                } catch (SftpException e) {
                    if (e.getMessage().contains("No such file")) {
                        log.warn("No existing analysis_result.json to delete for folder: {}", jsonFilePath);
                    } else {
                        throw e; // 다른 예외는 재발생
                    }
                }
            } else {
                log.info("JSON result file already exists for folder: {}", jsonFilePath);
                List<Map<String, Object>> existingResults = jsonService.loadResultsFromJsonSftp(folderPath, channelSftp);
                resultList.addAll(existingResults);
                return; // 추가 처리 건너뜁니다.
            }

        }
        // 결과를 새로 분석하는 로직
        List<Map<String, Object>> folderResultList = new ArrayList<>();
        List<Map<String, Object>> folderErrorList = new ArrayList<>();
        boolean isExcelFileProcessed = false;

        int availableCores = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = Executors.newFixedThreadPool(availableCores * 2);
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
                        processFile(channelSftp, folderPath, fileName, folderResultList, folderErrorList,processedImageIds, stopSubfolderSearch,passIds,duplicateJsonFiles);
                    } catch (Exception e) {
                        log.error("Error processing file: {}", fileName, e);
                    }
                }, executorService));

                isExcelFileProcessed = true;
                stopSubfolderSearch.set(true);
                break; // 첫 번째 엑셀 파일만 처리
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 특정 질환 폴더에 독립적으로 저장
        if (isExcelFileProcessed && targetDiseaseFolder != null) {
            log.info("Saving results independently to target disease folder: {}", targetDiseaseFolder);
            jsonService.saveResultsToJsonSftp("/내부 데이터", folderResultList, channelSftp);
            jsonService.saveResultsToJsonSftp("/내부 데이터", folderErrorList, channelSftp);// **독립된 리스트 저장**
        } else if (isExcelFileProcessed) {
            jsonService.saveResultsToJsonSftp(folderPath, folderResultList, channelSftp);
            jsonService.saveResultsToJsonSftp(folderPath, folderErrorList, channelSftp);
        }
        resultList.addAll(folderResultList);
        errorList.addAll(folderErrorList);

        // 하위 폴더 탐색 진행
        if (!stopSubfolderSearch.get()) {
            for (ChannelSftp.LsEntry entry : files) {
                if (entry.getAttrs().isDir() && !entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    if (folderPath.endsWith("/Labelling/Labelling") || folderPath.endsWith("\\Labelling\\Labelling")) {
                        log.info("Skipping folder: {}", folderPath);
                        continue;
                    }

                    String subFolderPath = folderPath + "/" + entry.getFilename();
                    processFolderRecursively(channelSftp, subFolderPath, resultList, errorList,processedImageIds, refresh,passIds,duplicateJsonFiles);
                }
            }
        }

        executorService.shutdown();
    }


    private String getTargetInstitutionFolder(String folderPath) {
        for (String institution : INSTITUTION_FOLDER_NAMES) {
            if (folderPath.contains(institution)) {
                return folderPath.substring(0, folderPath.indexOf(institution) + institution.length());
            }
        }
        return null; // 포함되지 않는 경우
    }


    private Boolean processJsonInputStream(InputStream jsonInputStream) throws IOException {
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
            return true;
        }
        return false;
    }

    // 질환별 폴더 확인 로직
    private void processFile(ChannelSftp channelSftp, String folderPath, String fileName,
                             List<Map<String, Object>> resultList, List<Map<String, Object>> errorList, Set<String> processedImageIds,
                             AtomicBoolean stopSubfolderSearch, List<String> passIds, Map<String, Map<String, List<String>>> duplicateJsonFiles) throws Exception {

        // JSON 파일에서 DISEASE_CLASS와 INSTITUTION_ID 추출
        String diseaseClass = null;
        String institutionId = null;

        if (folderPath.contains("치주질환")) {
            diseaseClass = "치주질환";
        } else if (folderPath.contains("두개안면")) {
            diseaseClass = "두개안면";
        } else if (folderPath.contains("구강암")) {
            diseaseClass = "구강암";
        } else if (folderPath.contains("골수염")) {
            diseaseClass = "골수염";
        } else if (folderPath.contains("대조군")) {
            diseaseClass = "골수염(대조군)";
        } else {
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
        } else if (folderPath.contains("서울대")) {
            institutionId = "서울대학교";
        } else if (folderPath.contains("원광대")) {
            institutionId = "원광대학교";
        } else if (folderPath.contains("조선대")) {
            institutionId = "조선대학교";
        } else {
            log.warn("Unknown institution in folder path: {}", folderPath);
        }

        // DISEASE_CLASS와 INSTITUTION_ID가 모두 추출되지 않았을 경우 처리
        if (diseaseClass == null || institutionId == null) {
            log.warn("Unable to determine DISEASE_CLASS or INSTITUTION_ID for file: {}", fileName);
            return; // 필수 데이터가 없으면 중단
        }

        String storagePath = storageConfig.getStoragePath();
        String uuid = UUID.randomUUID().toString();
        String newFileName = String.format("%s_%s_%s_%s_%s", diseaseClass, institutionId, fileName, uuid, ".xlsx");
        jsonService.saveExcelToLocal(channelSftp, folderPath, fileName, newFileName);

        InputStream inputStream = SFTPClient.readFile(channelSftp, folderPath, fileName);
        List<Map<String, Object>> filteredData = excelService.processExcelFile(inputStream, diseaseClass);

        String jsonPath = folderPath.contains("치주질환") ? folderPath + "/Labelling/meta" : folderPath + "/Labelling";
        Set<String> jsonFiles = folderFileCacheManager.computeIfAbsent(jsonPath, path -> {
            List<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, path);
            return files.stream()
                    .map(ChannelSftp.LsEntry::getFilename)
                    .filter(name -> name.endsWith(".json"))
                    .collect(Collectors.toSet());
        });

        // 현재 파일의 고유 ID 추출
        Set<String> fileImageIds = filteredData.stream()
                .map(row -> (String) row.get("IMAGE_ID"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 전체 중복 제거를 고려한 고유 ID 계산
        Set<String> newUniqueIds = new HashSet<>();
        synchronized (processedImageIds) {
            for (String imageId : fileImageIds) {
                if (!processedImageIds.contains(imageId)) {
                    newUniqueIds.add(imageId); // 전역 Set에 없는 경우 추가
                    processedImageIds.add(imageId); // 전역 Set 업데이트
                }
            }
        }

        // 중복 제거를 위한 Set
        Set<String> uniqueDcmFiles = new HashSet<>();
        Set<String> uniqueJsonFiles = new HashSet<>();
        Set<String> uniqueDrawingFiles = new HashSet<>();

        int dcmExistsCount = 0;
        int metaCount = 0;
        int drawingCount = 0;

        for (String imageId : fileImageIds) {
            // DCM 파일 중복 확인
            if (!uniqueDcmFiles.contains(imageId)) {
                boolean dcmExists = sftpService.checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".dcm", "");
                if (dcmExists) {
                    uniqueDcmFiles.add(imageId);
                    dcmExistsCount++;
                }
            }

            // JSON 파일 중복 확인
            if (!uniqueJsonFiles.contains(imageId)) {
                boolean jsonExists = jsonFiles.contains(imageId + ".json");
                if (jsonExists) {
                    uniqueJsonFiles.add(imageId);
                    metaCount++;
                }
            }

            // Drawing 파일 중복 확인
            if (!uniqueDrawingFiles.contains(imageId)) {
                boolean iniExists = sftpService.checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");
                if (iniExists) {
                    uniqueDrawingFiles.add(imageId);
                    drawingCount++;
                }
            }
        }

        // 결과 업데이트
        if (folderPath.contains("치주질환")) {
            incrementStatus(resultList, institutionId, diseaseClass, null, "임상", newUniqueIds.size());
            incrementStatus(resultList, institutionId, diseaseClass, null, "영상", dcmExistsCount);
            incrementStatus(resultList, institutionId, diseaseClass, null, "메타", metaCount);
        } else if (folderPath.contains("두개안면")) {
            dcmExistsCount = sftpService.countFilteredFoldersInPath(channelSftp, folderPath, "_");
            incrementStatus(resultList, institutionId, diseaseClass, null, "임상", newUniqueIds.size());
            incrementStatus(resultList, institutionId, diseaseClass, null, "영상", dcmExistsCount);
            incrementStatus(resultList, institutionId, diseaseClass, null, "메타", metaCount);
        }  else if (folderPath.contains("대조군")) {

            incrementStatus(resultList, institutionId, diseaseClass, "대조군", "임상", newUniqueIds.size());
            folderFileCacheManager.clearCache();

            Set<String> subFolderNames = folderFileCacheManager.computeIfAbsent(folderPath, path -> {
                try {
                    // SFTP에서 파일 목록 가져오기
                    List<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, path);

                    return files.stream()
                            .filter(entry -> {
                                if (entry.getAttrs() == null) {
                                    return false; // 속성이 null이면 제외
                                }

                                // 디렉토리인지 확인
                                if (!entry.getAttrs().isDir()) {
                                    return false;
                                }

                                String filename = entry.getFilename();

                                // ".", ".." 같은 잘못된 폴더 제외
                                if (filename == null || filename.equals(".") || filename.equals("..")) {
                                    return false;
                                }

                                // 특정 패턴을 제외 (".json", ".xlsx" 등)
                                return !filename.startsWith(".") && !filename.endsWith(".json") && !filename.endsWith(".xlsx");
                            })
                            .map(ChannelSftp.LsEntry::getFilename)
                            .collect(Collectors.toSet());

                } catch (SftpException e) {
                    // 권한 문제나 기타 오류 처리
                    if (e.getMessage().contains("Permission denied")) {
                        log.error("Permission denied for path: {}", path);
                    } else {
                        log.error("Error accessing path: {}", path, e);
                    }
                    return Collections.emptySet(); // 오류 발생 시 빈 Set 반환
                }
            });

            // 데이터 등록 건수 설정 (하위 폴더 수)
            incrementStatus(resultList, institutionId, diseaseClass, "대조군", "영상", subFolderNames.size());
            incrementStatus(resultList, institutionId, diseaseClass, "대조군", "메타", subFolderNames.size());

            // 라벨링 PASS 건수 계산 (하위 폴더 이름을 IMAGE_ID와 비교)
            for (String subFolderName : subFolderNames) {
                Optional<String> matchedImageId = fileImageIds.stream()
                        .filter(imageId ->
                                subFolderName.matches(".*\\b" + imageId + "\\b.*") || // 정확히 매칭 (정규식)
                                        subFolderName.contains("_" + imageId) // "NonIdentifying_" 형태로 매칭
                        )
                        .findFirst();

                if (matchedImageId.isPresent()) {
                    if (!passIds.contains(matchedImageId.get())) {
                        passIds.add(matchedImageId.get()); // Pass된 ID 저장
                        incrementStatus(resultList, institutionId, diseaseClass, "대조군", "drawing", null);
                        incrementStatus(resultList, institutionId, diseaseClass, "대조군", "라벨링pass건수", null);
                    }
                }
            }
            stopSubfolderSearch.set(true); // 대조군은 하위 폴더 탐색 중지
            return; // 대조군 처리 후 종료

            } else {
                int filteredFoldersCount = sftpService.countFilteredFoldersInPath(channelSftp, folderPath, "_");
                incrementStatus(resultList, institutionId, diseaseClass, null, "임상", newUniqueIds.size());
                incrementStatus(resultList, institutionId, diseaseClass, null, "영상", filteredFoldersCount);
                incrementStatus(resultList, institutionId, diseaseClass, null, "메타", metaCount);
            }

    // 엑셀 파일에서 추출된 IMAGE_ID와 JSON에서 얻은 DISEASE_CLASS, INSTITUTION_ID를 매핑하여 처리
        for (String imageId : fileImageIds) {

            // 파일 존재 여부를 확인하는 부분 (치주질환 폴더 확인)
            boolean dcmExists = false;
            boolean jsonExists = false;
            boolean iniExists = false;
            boolean alveExists = false;
            boolean labellingExists = false;


            Map<String, Object> errorData = new HashMap<>();
            errorData.put("institutionId", institutionId);
            errorData.put("diseaseClass" , diseaseClass);


            List<Map<String,Object>> filesList = new ArrayList<>();

            if (folderPath.contains("치주질환")) {

                dcmExists = sftpService.checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".dcm", "");
                jsonExists = sftpService.checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling/meta");
                iniExists = sftpService.checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");
                alveExists = sftpService.checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".png", "/Labelling/alve");

                if(iniExists && alveExists) {
                    incrementStatus(resultList, institutionId, diseaseClass, null, "drawing", null);
                }
                if(jsonExists) {
                    if ((dcmExists && iniExists && alveExists)) {
                        if (!passIds.contains(imageId)) {
                            passIds.add(imageId);
                            processJsonFile(channelSftp, folderPath, imageId, institutionId, diseaseClass, newFileName);
                            incrementStatus(resultList, institutionId, diseaseClass, null, "라벨링pass건수", null);
                            stopSubfolderSearch.set(true);  // 이 시점에서 하위 폴더 탐색을 중지
                        }
                    } else {
                        errorDataStatus(errorList, institutionId, diseaseClass, imageId,jsonExists,dcmExists,iniExists,alveExists);
                        stopSubfolderSearch.set(true);  // 이 시점에서 하위 폴더 탐색을 중지
                    }
                }
                else {
                    errorDataStatus(errorList, institutionId, diseaseClass, imageId,jsonExists,dcmExists,iniExists,alveExists);
                }
            }
            else if (folderPath.contains("두개안면")) {
                jsonExists = sftpService.checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling");
                dcmExists = sftpService.checkFileExistsInSFTPForImageId(channelSftp, folderPath, imageId);

                // JSON 파일 개수로 라벨링등록건수 설정
                if (jsonExists ) {
                    try (InputStream jsonInputStream = SFTPClient.readFile(channelSftp, folderPath+"/Labelling", imageId + ".json")) {
                        // JSON 데이터 처리
                        labellingExists = processJsonInputStream(jsonInputStream);
                        if(labellingExists && dcmExists) {
                            if (!passIds.contains(imageId)) {  // 중복 체크
                                passIds.add(imageId);
                                processJsonFile(channelSftp, folderPath, imageId, institutionId, diseaseClass, newFileName);
                                incrementStatus(resultList, institutionId, diseaseClass, null, "라벨링pass건수", null);
                            }
                        }
                        else{
                            errorDataStatus(errorList, institutionId, diseaseClass, imageId,jsonExists,dcmExists,false,false);
                        }  // 이 시점에서 하위 폴더 탐색을 중지
                    } catch (Exception e) {
                        log.error("Error processing JSON file for Image ID: {}", imageId, e);
                    }
                }
                else {
                    errorDataStatus(errorList, institutionId, diseaseClass, imageId,jsonExists,dcmExists,false,false);
                }
            }

            else {
                dcmExists = sftpService.checkFileExistsInSFTPForImageId(channelSftp, folderPath, imageId);
                jsonExists = sftpService.checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling");
                iniExists = sftpService.checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");
                labellingExists = sftpService.checkLabellingFileExistsInSFTPForImageId(channelSftp,folderPath+"/Labelling/Labelling", imageId);

                if(iniExists && labellingExists) {
                    incrementStatus(resultList, institutionId, diseaseClass, null, "drawing", null);
                }

                if (jsonExists && dcmExists) {
                    incrementStatus(resultList, institutionId, diseaseClass, null, "라벨링등록건수",null);
                    if ((labellingExists && iniExists)) {
                        if (!passIds.contains(imageId)) {
                            passIds.add(imageId);
                            processJsonFile(channelSftp, folderPath, imageId, institutionId, diseaseClass, newFileName);
                            incrementStatus(resultList, institutionId, diseaseClass, null, "라벨링pass건수", null);
                            stopSubfolderSearch.set(true);  // 이 시점에서 하위 폴더 탐색을 중지
                        }
                    } else {
                        errorDataStatus(errorList, institutionId, diseaseClass, imageId,jsonExists,dcmExists,iniExists,labellingExists);
                        stopSubfolderSearch.set(true);  // 이 시점에서 하위 폴더 탐색을 중지
                    }
                }
                else{
                    errorDataStatus(errorList, institutionId, diseaseClass, imageId,jsonExists,dcmExists,iniExists,labellingExists);

                }
            }

        }

    }

    /**
     *JSON값 불러오기
     */
    private void processJsonFile(ChannelSftp channelSftp, String folderPath, String imageId, String institutionId,
                                 String diseaseClass, String excelFileName) throws Exception {

        // JSON 파일 경로 설정
        String jsonFilePath = folderPath + (folderPath.contains("치주질환") ? "/Labelling/meta/" : "/Labelling/");

        try (InputStream jsonFileStream = SFTPClient.readFile(channelSftp, jsonFilePath, imageId + ".json")) {

            // ObjectMapper 사용하여 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonFileStream);

            // JSON에 엑셀 파일 이름 추가
            ((ObjectNode) rootNode).put("excelFileName", excelFileName);

            // JSON 내용을 로컬에 저장
            String savePath = "C:/app/disease_json/"; // 로컬 저장 경로
            String fileName = String.format("%s_%s.json", diseaseClass, institutionId);

            // JSON 데이터를 로컬에 저장
            jsonService.saveJsonToLocal(savePath, fileName, rootNode);

        } catch (Exception e) {
            log.error("Error while processing JSON file for Image ID: {}", imageId, e);
        }
    }


    // 값 저장하는 함수
    private void errorDataStatus(List<Map<String, Object>> errorList, String institutionId, String diseaseClass,
                                 String imageId, boolean json,boolean dcm,boolean ini,boolean labelling) {
        Optional<Map<String, Object>> existing = errorList.stream()
                .filter(item -> institutionId.equals(item.get("INSTITUTION_ID"))
                        && diseaseClass.equals(item.get("DISEASE_CLASS"))
                        && imageId.equals(item.get("image_id"))) // image_id 조건 추가
                .findFirst();


        // 해당 항목이 없다면 새로 추가합니다.
        if (existing.isEmpty()) {
            Map<String, Object> newEntry = new HashMap<>();
            newEntry.put("image_id", imageId);
            newEntry.put("INSTITUTION_ID", institutionId);
            newEntry.put("DISEASE_CLASS", diseaseClass);  // DISEASE_CLASS 추가
            newEntry.put("dcm_file", dcm);  // dcm
            newEntry.put("json_file", json);  // crf
            newEntry.put("ini_file", ini);  // json
            newEntry.put("labelling_file", labelling);  // ini
            errorList.add(newEntry);
            existing = Optional.of(newEntry);
        }

    }



    // 값 저장하는 함수
    private void incrementStatus(List<Map<String, Object>> resultList, String institutionId, String diseaseClass,
                                 String groupData, String status, Integer incrementValue) {
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
            newEntry.put("GROUP_TYPE", groupData);  // DISEASE_CLASS 추가
            newEntry.put("영상", 0);  // dcm
            newEntry.put("임상", 0);  // crf
            newEntry.put("메타", 0);  // json
            newEntry.put("drawing", 0);  // ini
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

}