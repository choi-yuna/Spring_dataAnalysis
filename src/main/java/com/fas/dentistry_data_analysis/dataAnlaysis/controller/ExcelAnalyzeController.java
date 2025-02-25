package com.fas.dentistry_data_analysis.dataAnlaysis.controller;

import com.fas.dentistry_data_analysis.dataAnlaysis.DTO.AnalysisRequestDTO;
import com.fas.dentistry_data_analysis.config.StorageConfig;
import com.fas.dentistry_data_analysis.dashboard.Service.AnalyzeBoardServiceImpl;
import com.fas.dentistry_data_analysis.dataAnlaysis.service.AnalyzeDataService;
import com.fas.dentistry_data_analysis.dataAnlaysis.service.duplication.DuplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final AnalyzeDataService jsonService;
    private final AnalyzeDataService excelService;
    private final AnalyzeDataService folderService;
    private final StorageConfig  storageConfig;
    private final DuplicationService duplicationService;

    @Autowired
    public ExcelAnalyzeController(@Qualifier("analyzeDataServiceImpl") AnalyzeDataService excelService,
                                  @Qualifier("analyzeFolderDataServiceImpl") AnalyzeDataService folderService,
                                  @Qualifier("analyzeJsonDataServiceImpl") AnalyzeDataService jsonService,
                                  StorageConfig storageConfig, DuplicationService duplicationService ) {
        this.excelService = excelService;
        this.folderService = folderService;
        this.jsonService = jsonService;
        this.storageConfig = storageConfig;
        this.duplicationService = duplicationService;
    }

    /**
     * 폴더 경로를 통한 CRF 분석
     * @param
     * @return
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeData(@RequestBody AnalysisRequestDTO request) {
        try {
            List<String> fileIds = request.getFileIds() != null ? Arrays.asList(request.getFileIds()) : new ArrayList<>();
            String storagePath = storageConfig.getStoragePath();
            String diseaseClass = request.getDiseaseClass();
            int institutionId = request.getInstitutionId();
            log.info("Analyzing data for file IDs: {}, diseaseClass: {}, institutionId: {}", fileIds, diseaseClass, institutionId);

            if (fileIds != null && fileIds.size() > 0 && !fileIds.contains("json")) { // null 및 "json" 포함 여부 체크
                List<Map<String, Map<String, String>>> dataList = excelService.analyzeData(fileIds, diseaseClass, institutionId);
                return ResponseEntity.ok(Map.of("data", dataList));
            } else if (fileIds != null && fileIds.size() == 1 && "json".equals(fileIds.get(0))) { // fileIds가 "json" 문자열 하나만 포함하는 경우 처리
                List<Map<String, Map<String, String>>> dataList = jsonService.analyzeData(List.of("C:/app/disease_json"), diseaseClass, institutionId);

                List<Map<String, Map<String, String>>> metaData = List.of();
                if(diseaseClass.equals("0") && institutionId == 0) {
                    metaData = folderService.analyzeData(List.of("C:/app/dentistry"), "E", institutionId);
                }
                return ResponseEntity.ok(Map.of("data", dataList, "meta", metaData.size()));
            } else { // fileIds가 null이거나 비어 있는 경우 처리
                List<Map<String, Map<String, String>>> dataList = folderService.analyzeData(List.of("C:/app/dentistry"), diseaseClass, institutionId);
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

            String storagePath = storageConfig.getStoragePath();

            // 필터 조건 추출 (INSTITUTION_ID, P_GENDER 등)
            Map<String, String> filters = new HashMap<>();
            filterRequest.forEach((key, value) -> {
                if (!key.equals("fileIds") && !key.equals("header")) {  // fileIds와 header는 제외
                    filters.put(key, value.toString());
                }
            });

            // header 값 추출
            List<String> headers = (List<String>) filterRequest.get("header");

            // JSON 데이터 처리
            if (fileIdsList != null && "json".equals(fileIdsList.get(0))) {
                List<Map<String, Object>> filteredDataList = jsonService.analyzeDataWithFilters(List.of("C:/app/disease_json"), filters, headers);
                return ResponseEntity.ok(filteredDataList);
            }
            // 일반 데이터 처리
            if (fileIdsList != null) { // fileIds가 있을 경우
                List<String> fileIds = List.of(fileIdsList.toArray(new String[0]));
                List<Map<String, Object>> filteredDataList = excelService.analyzeDataWithFilters(fileIds, filters, headers);
                return ResponseEntity.ok(filteredDataList);
            } else { // fileIds가 없을 경우
                List<Map<String, Object>> filteredDataList = folderService.analyzeDataWithFilters(List.of("C:/app/dentistry"), filters, headers);
                return ResponseEntity.ok(filteredDataList);
            }

        } catch (IOException e) {
            log.error("IOException occurred during data analysis", e);
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
