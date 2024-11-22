package com.fas.dentistry_data_analysis.service.dashBoard;

import com.fas.dentistry_data_analysis.util.SFTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
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

    // 생성자에서 executorService를 주입받음
    public AnalyzeBoardServiceImpl(DataGropedService dataGropedService, ExcelService excelService) {
        this.dataGropedService = dataGropedService;
        this.excelService = excelService;
    }

    public Map<String, Object> processFilesInFolder(String folderPath) throws Exception {
        List<Map<String, Object>> resultList = new ArrayList<>();

        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            session = SFTPClient.createSession(SFTP_HOST, SFTP_USER, SFTP_PASSWORD, SFTP_PORT);
            channelSftp = SFTPClient.createSftpChannel(session);

            // 폴더 내 파일을 병렬로 처리
            processFolderRecursively(channelSftp, folderPath, resultList);
        } finally {
            if (channelSftp != null) channelSftp.disconnect();
            if (session != null) session.disconnect();
            log.info("SFTP connection closed");
        }

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> diseaseData = dataGropedService.groupDataByDisease(resultList);
        diseaseData.add(dataGropedService.createAllData(resultList, "INSTITUTION_ID", "질환 ALL"));
        response.put("질환별", diseaseData);

        List<Map<String, Object>> institutionData = dataGropedService.groupDataByInstitution(resultList);
        institutionData.add(dataGropedService.createAllData(resultList, "DISEASE_CLASS", "기관 ALL"));
        response.put("기관별", institutionData);

        response.put("대시보드", getDashboardData(resultList));

        return response;
    }

    private void processFolderRecursively(ChannelSftp channelSftp, String folderPath, List<Map<String, Object>> resultList) throws Exception {
        // 스레드 안전한 ConcurrentSkipListSet을 사용하여 중복된 파일을 방지
        Set<String> processedFiles = new ConcurrentSkipListSet<>();

        // 폴더 내 모든 .xlsx 파일 목록을 가져옵니다.
        Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, folderPath);
        log.info("Found {} files in folder: {}", files.size(), folderPath);

        // ExecutorService 생성 (파일을 병렬로 처리하기 위해)
        int availableCores = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = Executors.newFixedThreadPool(availableCores);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ChannelSftp.LsEntry entry : files) {
            String fileName = entry.getFilename();
            if (fileName.endsWith(".xlsx")) {
                // synchronized 블록을 사용하여 중복 처리 방지
                synchronized (processedFiles) {
                    if (processedFiles.contains(fileName)) {
                        continue;  // 이미 처리된 파일은 건너뜀
                    }
                    processedFiles.add(fileName);  // 파일을 처리 목록에 추가
                }

                // 각 .xlsx 파일에 대한 처리 작업을 병렬로 실행할 CompletableFuture로 래핑
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        processFile(channelSftp, folderPath, fileName, resultList);
                    } catch (Exception e) {
                        log.error("Error processing file: {}", fileName, e);
                    }
                }, executorService));
            }
        }

        // 모든 파일 처리 완료까지 기다림
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 하위 폴더 재귀 호출
        for (ChannelSftp.LsEntry entry : files) {
            if (entry.getAttrs().isDir() && !entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                processFolderRecursively(channelSftp, folderPath + "/" + entry.getFilename(), resultList);
            }
        }
        // Executor 종료
        executorService.shutdown();
    }

    private void processFile(ChannelSftp channelSftp, String folderPath, String fileName, List<Map<String, Object>> resultList) throws Exception {
        log.info("Processing file: {}", fileName);
        InputStream inputStream = SFTPClient.readFile(channelSftp, folderPath, fileName);

        // 엑셀 파일 처리
        List<Map<String, Object>> filteredData = excelService.processExcelFile(inputStream);

        // ConcurrentLinkedQueue 사용 (스레드 안전한 큐)
        Queue<Map<String, Object>> concurrentResultList = new ConcurrentLinkedQueue<>();
        concurrentResultList.addAll(filteredData);
        synchronized (resultList) {
            resultList.addAll(concurrentResultList);  // 추가된 데이터를 resultList에 병합
        }
        log.info("Processed {} rows from file: {}", filteredData.size(), fileName);

        // 상태 업데이트
        for (Map<String, Object> row : filteredData) {
            String imageId = (String) row.get("IMAGE_ID");
            String diseaseClass = (String) row.get("DISEASE_CLASS");
            String institutionId = (String) row.get("INSTITUTION_ID");

            boolean dcmExists = false;
            boolean iniExists = false;
            boolean jsonExists = false;

            if (folderPath.contains("치주질환")) {
                log.info("Processed {} {}", imageId, folderPath);
                dcmExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".dcm", "");
                jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling/meta");
                iniExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");

                if (!(dcmExists && jsonExists && iniExists)) {
                    incrementStatus(resultList, institutionId, diseaseClass, imageId, "데이터구성검수");
                } else {
                    if (jsonExists) {
                        processJsonFile(channelSftp, folderPath, imageId, resultList, institutionId, diseaseClass);
                    }
                }

            } else {
                dcmExists = checkFileExistsInSFTPForImageId(channelSftp, folderPath, imageId);
                log.info("{}",dcmExists);
                jsonExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".json", "/Labelling");
                log.info("{}",jsonExists);
                iniExists = checkFileExistsInSFTP(channelSftp, folderPath, imageId + ".ini", "/Labelling/draw");
                log.info("{}",iniExists);

                if (!(dcmExists && jsonExists && iniExists)) {
                    incrementStatus(resultList, institutionId, diseaseClass, imageId, "데이터구성검수");
                } else {
                    if (jsonExists) {
                        processJsonFile(channelSftp, folderPath, imageId, resultList, institutionId, diseaseClass);
                    }
                }
            }
        }
    }

    private boolean checkFileExistsInSFTPForImageId(ChannelSftp channelSftp, String folderPath, String imageId) throws SftpException {
        // 폴더 내 모든 파일과 디렉터리 목록을 가져옵니다.
        Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, folderPath);

        // 해당 폴더 내에서 imageId를 포함한 폴더를 찾습니다
        for (ChannelSftp.LsEntry entry : files) {
            if (entry.getAttrs().isDir() && entry.getFilename().contains(imageId)) {  // imageId가 포함된 폴더 찾기
                String targetFolderPath = folderPath + "/" + entry.getFilename();  // 해당 폴더 경로

                // 해당 폴더 내에서 .dcm 확장자를 가진 파일이 하나라도 있는지 확인
                Vector<ChannelSftp.LsEntry> subFiles = SFTPClient.listFiles(channelSftp, targetFolderPath);
                for (ChannelSftp.LsEntry subEntry : subFiles) {
                    if (subEntry.getFilename().endsWith(".dcm")) {
                        return true;  // .dcm 파일이 하나라도 있으면 true 반환
                    }
                }
            }
        }

        return false; // 해당 폴더 내에 .dcm 파일이 없으면 false 반환
    }


    private void processJsonFile(ChannelSftp channelSftp, String folderPath, String imageId, List<Map<String, Object>> resultList, String institutionId, String diseaseClass) throws Exception {

        // JSON 파일 경로 설정
        String jsonFilePath = folderPath + (folderPath.contains("치주질환") ? "/Labelling/meta/" : "/Labelling/");
        String labelingKey = folderPath.contains("치주질환") ? "Labeling_Info" : "Labeling_info";
        String firstCheckKey = folderPath.contains("치주질환") ? "First_Check_Info" : "First_Check_info";
        String secondCheckKey = folderPath.contains("치주질환") ? "Second_Check_Info" : "Second_Check_info";

        // JSON 파일을 한 번만 읽음
        InputStream jsonFileStream = SFTPClient.readFile(channelSftp, jsonFilePath, imageId + ".json");

        // ObjectMapper 사용하여 JSON 파싱
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonFileStream);

        // 각 상태를 한 번에 추출
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
    }


    private Map<String, Object> getDashboardData(List<Map<String, Object>> resultList) {
        int totalFilesCount = resultList.size();
        long errorFilesCount = resultList.stream()
                .filter(row -> "데이터구성검수".equals(row.get("status")))
                .count();
        long secondCheck = resultList.stream()
                .filter(row -> "2차검수".equals(row.get("status")))
                .count();
        String uploadDate = LocalDate.now().toString();

        List<Map<String, Object>> statuses = new ArrayList<>();
        Map<String, Object> totalFilesStatus = new HashMap<>();
        totalFilesStatus.put("totalFiles", "총파일 수");
        totalFilesStatus.put("fileCount", totalFilesCount);
        totalFilesStatus.put("uploadDate", uploadDate);
        totalFilesStatus.put("totalFilesCount", totalFilesCount);
        statuses.add(totalFilesStatus);

        Map<String, Object> errorFilesStatus = new HashMap<>();
        errorFilesStatus.put("totalFiles", "오류 파일 수");
        errorFilesStatus.put("fileCount", errorFilesCount);
        errorFilesStatus.put("uploadDate", uploadDate);
        errorFilesStatus.put("totalFilesCount", totalFilesCount);
        statuses.add(errorFilesStatus);

        Map<String, Object> builtRateStatus = new HashMap<>();
        builtRateStatus.put("totalFiles", "구축율");
        builtRateStatus.put("fileCount", secondCheck);
        builtRateStatus.put("uploadDate", uploadDate);
        builtRateStatus.put("totalFilesCount", totalFilesCount);
        builtRateStatus.put("showGraph", "ture");
        statuses.add(builtRateStatus);

        Map<String, Object> dashboardData = new HashMap<>();
        dashboardData.put("statuses", statuses);
        return dashboardData;
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


    // SFTP에서 파일 존재 여부 확인 (하위 폴더 경로 포함)
// 캐시를 저장하는 Map
    private final Map<String, Set<String>> fileCache = new HashMap<>();

// SFTP에서 파일 존재 여부 확인 (하위 폴더 경로 포함)
    private boolean checkFileExistsInSFTP(ChannelSftp channelSftp, String folderPath, String fileName, String subFolder) throws SftpException {
        // 하위 폴더 경로를 포함한 전체 경로로 파일을 찾음
        String targetPath = folderPath + subFolder;

        // 캐시에서 파일 목록을 가져오거나, 캐시에 없는 경우 새로 가져옴
        Set<String> cachedFiles = fileCache.get(targetPath);
        if (cachedFiles == null) {
            // 캐시에 없으면 SFTP 서버에서 파일 목록을 가져옵니다.
            try {
                Vector<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, targetPath);
                cachedFiles = new HashSet<>();
                for (ChannelSftp.LsEntry entry : files) {
                    cachedFiles.add(entry.getFilename());
                }
                // 파일 목록을 캐시에 저장
                fileCache.put(targetPath, cachedFiles);
            } catch (SftpException e) {
                // 해당 폴더가 존재하지 않으면 false 반환
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    return false;
                }
                throw e;  // 다른 예외는 다시 던지기
            }
        }

        // 캐시에서 해당 파일이 있는지 확인
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