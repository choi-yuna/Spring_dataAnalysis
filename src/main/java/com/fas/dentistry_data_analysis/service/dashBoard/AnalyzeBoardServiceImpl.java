package com.fas.dentistry_data_analysis.service.dashBoard;

import com.fas.dentistry_data_analysis.service.FolderMetadataService;
import com.fas.dentistry_data_analysis.util.SFTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class AnalyzeBoardServiceImpl {

    // SFTP 서버 정보
    private static final String SFTP_HOST = "202.86.11.27";  // SFTP 서버 IP
    private static final int SFTP_PORT = 22;  // SFTP 포트
    private static final String SFTP_USER = "dent_fas";  // 사용자 계정
    private static final String SFTP_PASSWORD = "dent_fas123";  // 비밀번호

    private final DataGropedService dataGropedService;
    private final ExcelService excelService;
    private final FolderMetadataService folderMetadataService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2 * Runtime.getRuntime().availableProcessors());

    @Autowired
    public AnalyzeBoardServiceImpl(DataGropedService dataGropedService, ExcelService excelService, FolderMetadataService folderMetadataService) {
        this.dataGropedService = dataGropedService;
        this.excelService = excelService;
        this.folderMetadataService = folderMetadataService;
    }



    public Map<String, Object> processFilesInFolder(String folderPath) throws Exception {
        // 이미 분석된 폴더인지 확인

        List<Map<String, Object>> resultList = new ArrayList<>();
        Session session = null;
        ChannelSftp channelSftp = null;

        // 중복 처리용 전역 Set
        Set<String> processedImageIds = new HashSet<>(); // 여기에 중복된 ID를 저장

        try {
            session = SFTPClient.createSession(SFTP_HOST, SFTP_USER, SFTP_PASSWORD, SFTP_PORT);
            channelSftp = SFTPClient.createSftpChannel(session);

            // 폴더 내 파일을 병렬로 처리
            processFolderRecursively(channelSftp, folderPath, resultList, processedImageIds);
        } finally {
            if (channelSftp != null) channelSftp.disconnect();
            if (session != null) session.disconnect();
            log.info("SFTP connection closed");
        }

        Map<String, Object> response = new HashMap<>();

        // "질환 ALL" 데이터 먼저 추가
        List<Map<String, Object>> diseaseData = new ArrayList<>();
        diseaseData.add(dataGropedService.createAllData(resultList, "INSTITUTION_ID", "질환 ALL"));
        diseaseData.addAll(dataGropedService.groupDataByDisease(resultList)); // 그룹화된 데이터 추가
        response.put("질환별", diseaseData);

        // "기관 ALL" 데이터 먼저 추가
        List<Map<String, Object>> institutionData = new ArrayList<>();
        institutionData.add(dataGropedService.createAllData(resultList, "DISEASE_CLASS", "기관 ALL"));
        institutionData.addAll(dataGropedService.groupDataByInstitution(resultList)); // 그룹화된 데이터 추가
        response.put("기관별", institutionData);

        // 중복 체크를 위한 IMAGE_ID 처리
        Set<String> uniqueImageIds = new HashSet<>();
        int nullCount = 0; // null값 확인용 카운트

        for (Map<String, Object> row : resultList) {
            String imageId = (String) row.get("IMAGE_ID");

            // null값을 확인하고 카운트
            if (imageId == null) {
                nullCount++;
            } else {
                uniqueImageIds.add(imageId);
            }
        }
        return response;
    }

    private void processFolderRecursively(ChannelSftp channelSftp, String folderPath, List<Map<String, Object>> resultList, Set<String> processedImageIds) throws Exception {
        // 현재 폴더가 처리되었는지 확인
        if (folderMetadataService.isFolderAlreadyAnalyzed(folderPath)) {
            log.info("Folder {} is already analyzed. Skipping...", folderPath);
            return;
        }

        // 현재 폴더를 처리 중으로 마크하고 파일 분석
        folderMetadataService.saveAnalyzedFolder(folderPath);
        log.info("Processing folder: {}", folderPath);

        // 폴더 내 모든 파일 및 폴더 목록 가져오기
        Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, folderPath);
        log.info("Found {} files in folder: {}", files.size(), folderPath);

        // .xlsx 파일 처리
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ChannelSftp.LsEntry entry : files) {
            String fileName = entry.getFilename();

            if (fileName.endsWith(".xlsx")) {
                // 중복 체크: 이미 처리된 파일은 건너뜁니다.
                if (processedImageIds.contains(fileName)) {
                    log.info("File {} is already processed. Skipping...", fileName);
                    continue;
                }
                processedImageIds.add(fileName); // 파일을 처리 목록에 추가

                // 각 .xlsx 파일에 대한 처리 작업 실행
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        processFile(channelSftp, folderPath, fileName, resultList, processedImageIds, new AtomicBoolean(false));
                    } catch (Exception e) {
                        log.error("Error processing file: {}", fileName, e);
                    }
                }));
            }
        }

        // 모든 파일 처리 완료까지 기다림
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 하위 폴더 탐색
        for (ChannelSftp.LsEntry entry : files) {
            if (entry.getAttrs().isDir() && !entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                String subFolderPath = folderPath + "/" + entry.getFilename();

                // 하위 폴더가 처리되지 않은 경우만 처리
                if (!folderMetadataService.isFolderAlreadyAnalyzed(subFolderPath)) {
                    processFolderRecursively(channelSftp, subFolderPath, resultList, processedImageIds);
                } else {
                    log.info("Subfolder {} is already analyzed. Skipping...", subFolderPath);
                }
            }
        }
    }

    private void processFile(ChannelSftp channelSftp, String folderPath, String fileName, List<Map<String, Object>> resultList, Set<String> processedImageIds, AtomicBoolean stopSubfolderSearch) throws Exception {
        InputStream inputStream = SFTPClient.readFile(channelSftp, folderPath, fileName);

        // 엑셀 파일 처리
        List<Map<String, Object>> filteredData = excelService.processExcelFile(inputStream);

        // 중복된 IMAGE_ID 처리
        for (Map<String, Object> row : filteredData) {
            String imageId = (String) row.get("IMAGE_ID");

            // 이미 처리된 imageId는 건너뛰기
            if (processedImageIds.contains(imageId)) {
                continue;
            }

            // 아직 처리되지 않은 imageId는 처리 리스트에 추가
            processedImageIds.add(imageId);

            // 상태 업데이트
            String diseaseClass = (String) row.get("DISEASE_CLASS");
            String institutionId = (String) row.get("INSTITUTION_ID");
            if (institutionId == null) {
                continue; // null 데이터를 건너뜁니다.
            }

            // 파일 존재 여부를 확인하는 부분 (치주질환 폴더 확인)
            boolean dcmExists = false;
            boolean jsonExists = false;
            boolean iniExists = false;

            if (folderPath.contains("치주질환")) {
                dcmExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".dcm", "");
                jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling/meta");
                iniExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");
            } else {
                dcmExists = checkFileExistsInSFTPForImageId(channelSftp, folderPath, imageId);
                jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling");
                iniExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");
            }

            // 파일 존재 여부에 따라 상태 업데이트
            if (!(dcmExists && jsonExists && iniExists)) {
                incrementStatus(resultList, institutionId, diseaseClass, imageId, "데이터구성검수");
                stopSubfolderSearch.set(true);  // 이 시점에서 하위 폴더 탐색을 중지
            } else {
                if (jsonExists) {
                    processJsonFile(channelSftp, folderPath, imageId, resultList, institutionId, diseaseClass);
                }
                stopSubfolderSearch.set(true);  // 이 시점에서 하위 폴더 탐색을 중지
            }
        }

    }





    private boolean checkFileExistsInSFTPForImageId(ChannelSftp channelSftp, String folderPath, String imageId) throws SftpException {
        // 지정된 경로(folderPath) 내에서만 파일과 폴더를 검색합니다.
        Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, folderPath);

        // 주어진 경로에서 imageId와 일치하는 폴더를 찾습니다
        for (ChannelSftp.LsEntry entry : files) {
            // 폴더 이름에 imageId가 포함된 경우
            if (entry.getAttrs().isDir() && entry.getFilename().contains(imageId)) {
                String targetFolderPath = folderPath + "/" + entry.getFilename();  // 해당 폴더 경로

                // 해당 폴더 내에서 .dcm 확장자를 가진 파일이 하나라도 있는지 확인
                Vector<ChannelSftp.LsEntry> subFiles = SFTPClient.listFiles(channelSftp, targetFolderPath);
                for (ChannelSftp.LsEntry subEntry : subFiles) {
                    if (subEntry.getFilename().endsWith(".dcm")) {
                        return true;  // .dcm 파일이 하나라도 있으면 true 반환
                    }
                }

                // .dcm 파일을 찾지 못한 경우 false 반환
                return false;
            }
        }

        // 주어진 경로 내에서 imageId를 포함하는 폴더가 없으면 false 반환
        return false;
    }
    private void processJsonFile(ChannelSftp channelSftp, String folderPath, String imageId, List<Map<String, Object>> resultList, String institutionId, String diseaseClass) throws Exception {

        // JSON 파일 경로 설정
        String jsonFilePath = folderPath + (folderPath.contains("치주질환") ? "/Labelling/meta/" : "/Labelling/");
        String labelingKey = folderPath.contains("치주질환") ? "Labeling_Info" : "Labeling_info";
        String firstCheckKey = folderPath.contains("치주질환") ? "First_Check_Info" : "First_Check_info";
        String secondCheckKey = folderPath.contains("치주질환") ? "Second_Check_Info" : "Second_Check_info";


        try (InputStream jsonFileStream = SFTPClient.readFile(channelSftp, jsonFilePath, imageId + ".json")) {

            // ObjectMapper 사용하여 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonFileStream);


            // 상태 추출
            boolean labelingStatus = getJsonStatus(rootNode, labelingKey) == 2;
            boolean firstCheckStatus = getJsonStatus(rootNode, firstCheckKey) == 2;
            boolean secondCheckStatus = getJsonStatus(rootNode, secondCheckKey) == 2;


            // 상태가 2인 경우에만 처리
            if (labelingStatus) {
                incrementStatus(resultList, institutionId, diseaseClass, imageId, "라벨링건수");
            }
            if (firstCheckStatus) {
                incrementStatus(resultList, institutionId, diseaseClass, imageId, "1차검수");
            }
            if (secondCheckStatus) {
                incrementStatus(resultList, institutionId, diseaseClass, imageId, "2차검수");
            }
        } catch (Exception e) {
            log.error("Error while processing JSON file for Image ID: {}", imageId, e);
        }
    }

    // 중복된 IMAGE_ID를 처리하는 방식 개선
    private void incrementStatus(List<Map<String, Object>> resultList, String institutionId, String diseaseClass, String imageId, String status) {
        // IMAGE_ID, INSTITUTION_ID, DISEASE_CLASS를 기준으로 항목을 찾습니다.
        Optional<Map<String, Object>> existing = resultList.stream()
                .filter(item -> imageId.equals(item.get("IMAGE_ID")) && institutionId.equals(item.get("INSTITUTION_ID"))
                        && (diseaseClass != null && diseaseClass.equals(item.get("DISEASE_CLASS"))))  // null 체크 추가
                .findFirst();
        // 해당 항목이 없다면 새로 추가합니다.
        if (existing.isEmpty()) {
            Map<String, Object> newEntry = new HashMap<>();
            newEntry.put("IMAGE_ID", imageId);
            newEntry.put("INSTITUTION_ID", institutionId);
            newEntry.put("DISEASE_CLASS", diseaseClass);  // diseaseClass 값도 추가
            newEntry.put("목표건수", 0);  // 목표건수는 나중에 계산
            newEntry.put("라벨링건수", 0);
            newEntry.put("1차검수", 0);
            newEntry.put("데이터구성검수", 0);
            newEntry.put("2차검수", 0);
            newEntry.put("목표건수_증가", false);  // 목표건수 증가 여부를 초기값 false로 설정
            resultList.add(newEntry);
            existing = Optional.of(newEntry);
        }

        Map<String, Object> statusMap = existing.get();

        // 목표건수 증가 여부 체크 (Boolean 값을 null이 아닌 기본값인 false로 처리)
        boolean incrementGoalCount = false;

        if ("데이터구성검수".equals(status) && !(Boolean) statusMap.getOrDefault("목표건수_증가", false)){
            incrementGoalCount = true;
        }
        // 상태 값이 "라벨링건수", "1차검수", "2차검수"일 때 한 번만 목표건수를 증가시키도록 처리
        if ("라벨링건수".equals(status) && !(Boolean) statusMap.getOrDefault("목표건수_증가", false)) {
            incrementGoalCount = true;
        }
        if ("1차검수".equals(status) && !(Boolean) statusMap.getOrDefault("목표건수_증가", false)) {
            incrementGoalCount = true;
        }
        if ("2차검수".equals(status) && !(Boolean) statusMap.getOrDefault("목표건수_증가", false)) {
            incrementGoalCount = true;
        }

        // 목표건수 증가 여부가 true일 경우에만 목표건수를 증가시킴
        if (incrementGoalCount) {
            Integer currentGoalCount = (Integer) statusMap.get("목표건수");
            if (currentGoalCount == null) {
                currentGoalCount = 0;
            }
            statusMap.put("목표건수", currentGoalCount + 1);  // 목표건수 증가
            statusMap.put("목표건수_증가", true);  // 목표건수 증가 여부 표시
        }

        // 각 상태별 카운트 (라벨링건수, 1차검수, 2차검수) 증가
        Integer currentStatusValue = (Integer) statusMap.get(status);
        if (currentStatusValue == null) {
            currentStatusValue = 0;
        }
        statusMap.put(status, currentStatusValue + 1);
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




    // 여러 파일의 존재 여부를 한 번에 확인 (하위 폴더 포함)
    private Map<String, Boolean> checkMultipleFilesExist(ChannelSftp channelSftp, String folderPath, List<String> fileNames, String subFolder) throws SftpException {
        // 캐시에서 존재 여부를 먼저 확인
        Map<String, Boolean> resultMap = new HashMap<>();
        for (String fileName : fileNames) {
            resultMap.put(fileName, checkFileExistsInSFTP(channelSftp, folderPath, fileName, subFolder));
        }
        return resultMap;
    }


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