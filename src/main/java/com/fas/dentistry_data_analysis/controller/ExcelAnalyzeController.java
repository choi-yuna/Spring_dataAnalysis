package com.fas.dentistry_data_analysis.controller;

import com.fas.dentistry_data_analysis.DTO.AnalysisRequestDTO;
import com.fas.dentistry_data_analysis.config.StorageConfig;
import com.fas.dentistry_data_analysis.service.dashBoard.AnalyzeBoardServiceImpl;
import com.fas.dentistry_data_analysis.service.AnalyzeDataService;
import com.fas.dentistry_data_analysis.service.duplication.DuplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Slf4j
@RestController
@RequestMapping("/api")
public class ExcelAnalyzeController {

// private final String folderPath = "/치의학데이터 과제 데이터 수집/내부 데이터/단국대/골수염";
private final String folderPath = "/내부 데이터";


        private final AnalyzeDataService analyzeDataService;
        private final AnalyzeBoardServiceImpl analyzeBoardService;
        private final StorageConfig  storageConfig;
        private final DuplicationService duplicationService;

    @Autowired
    public ExcelAnalyzeController(AnalyzeDataService analyzeDataService, AnalyzeBoardServiceImpl analyzeBoardService, StorageConfig storageConfig,DuplicationService duplicationService ) {
        this.analyzeDataService = analyzeDataService;
        this.analyzeBoardService = analyzeBoardService;
        this.storageConfig = storageConfig;
        this.duplicationService = duplicationService;
    }

    @PostMapping("/dashboard")
    public ResponseEntity<?> dashboardData(@RequestParam(value = "refresh", defaultValue = "false") boolean refresh) throws Exception {
        log.info("{}",refresh);
        if (refresh && analyzeBoardService.isRefreshInProgress()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("새로고침이 이미 실행 중 입니다.");
        }
        // processFilesInFolder 메서드에 refresh 파라미터 전달
        Map<String, Object> stringObjectMap = analyzeBoardService.processFilesInFolder(folderPath, refresh);
        return ResponseEntity.ok(Map.of("data", stringObjectMap));
    }


    /**
     * 폴더 경로를 통한 CRF 분석
     * @param
     * @return
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeData(@RequestBody AnalysisRequestDTO request) {
        try {
            String[] fileIds = request.getFileIds();
           String storagePath = storageConfig.getStoragePath();
            String diseaseClass = request.getDiseaseClass();
            int institutionId = request.getInstitutionId();
            log.info("Analyzing data for file IDs: {}, diseaseClass: {}, institutionId: {}", fileIds, diseaseClass, institutionId);

            if (fileIds != null && fileIds.length > 0 && !Arrays.asList(fileIds).contains("json")) { // null 및 "json" 포함 여부 체크
                List<Map<String, Map<String, String>>> dataList = analyzeDataService.analyzeData(fileIds, diseaseClass, institutionId);
                return ResponseEntity.ok(Map.of("data", dataList));
            } else if (fileIds != null && fileIds.length == 1 && "json".equals(fileIds[0])) { // fileIds가 "json" 문자열 하나만 포함하는 경우 처리
                List<Map<String, Map<String, String>>> dataList = analyzeDataService.analyzeJsonData("C:/app/disease_json", diseaseClass, institutionId);
                return ResponseEntity.ok(Map.of("data", dataList));
            } else { // fileIds가 null이거나 비어 있는 경우 처리
                List<Map<String, Map<String, String>>> dataList = analyzeDataService.analyzeFolderData("C:/app/dentistry", diseaseClass, institutionId);
                return ResponseEntity.ok(Map.of("data", dataList));
            }


        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("데이터 분석 중 오류가 발생했습니다.");
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    @PostMapping("/analyze-filters")
    public ResponseEntity<?> analyzeDataWithFilters(@RequestBody Map<String, Object> filterRequest) {
        try {
            // fileIds 추출
            log.info("Analyzing data with filters: {}", filterRequest);
           List<String> fileIdsList = (List<String>) filterRequest.get("fileIds");
            log.info("Analyzing data : {}", fileIdsList);
            String StoragePath = storageConfig.getStoragePath();
            // 필터 조건 추출 (INSTITUTION_ID, P_GENDER 등)
            Map<String, String> filters = new HashMap<>();
            filterRequest.forEach((key, value) -> {
                if (!key.equals("fileIds") && !key.equals("header")) {  // fileIds와 header는 제외
                    filters.put(key, value.toString());
                }
            });

            // header 값 추출
            List<String> headers = (List<String>) filterRequest.get("header");
            //log.info("Analyzing data with filters for file IDs: {}, filters: {}, headers: {}", Arrays.toString(fileIds), filters, headers);
            if (fileIdsList != null ) { // null 체크 추가
                String[] fileIds = fileIdsList.toArray(new String[0]);
                List<Map<String, Object>> filteredDataList = analyzeDataService.analyzeDataWithFilters(fileIds, filters, headers);
                return ResponseEntity.ok(filteredDataList);
            }
            else {
                // 동적 필터링과 헤더 필터링을 수행하고 List<Map<String, Object>> 반환
                List<Map<String, Object>> filteredDataList = analyzeDataService.analyzeFolderDataWithFilters("C:/app/dentistry", filters, headers);
            return ResponseEntity.ok(filteredDataList);
            }
            // 변환된 List를 클라이언트에 반환

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("데이터 분석 중 오류가 발생했습니다.");
        }
    }

    @PostMapping("/error-analyze")
    public ResponseEntity<?> ErrorAnalyzeData(@RequestBody AnalysisRequestDTO request) {
        try {
            String[] fileIds = request.getFileIds();
            String diseaseClass = request.getDiseaseClass();
            int institutionId = request.getInstitutionId();
            log.info("Analyzing data for file IDs: {}, diseaseClass: {}, institutionId: {}", fileIds, diseaseClass, institutionId);
            Map<String, Object> stringObjectMap = duplicationService.extractAndCombineData("C:/app/dentistry", diseaseClass, institutionId);
            return ResponseEntity.ok(Map.of("data", stringObjectMap));

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException(ex);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }


    }

}
