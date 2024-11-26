package com.fas.dentistry_data_analysis.controller;

import com.fas.dentistry_data_analysis.DTO.AnalysisRequestDTO;
import com.fas.dentistry_data_analysis.service.dashBoard.AnalyzeBoardServiceImpl;
import com.fas.dentistry_data_analysis.service.AnalyzeDataService;
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

    private final String folderPath = "/치의학데이터 과제 데이터 수집/내부 데이터";


  //  private final DataAnalysisService dataAnalysisService;
        private final AnalyzeDataService analyzeDataService;
        private final AnalyzeBoardServiceImpl analyzeBoardService;

    @Autowired
    public ExcelAnalyzeController(AnalyzeDataService analyzeDataService, AnalyzeBoardServiceImpl analyzeBoardService ) {
        this.analyzeDataService = analyzeDataService;
        this.analyzeBoardService = analyzeBoardService;
    }

    @PostMapping("/dashboard")
    public ResponseEntity<?> dashboardData() throws Exception {
        Map<String, Object>   stringObjectMap = analyzeBoardService.processFilesInFolder(folderPath);
        return ResponseEntity.ok(Map.of("data", stringObjectMap));
    }
    // 기존 분석 API
//    @PostMapping("/analyze")
//    public ResponseEntity<?> analyzeData(@RequestBody AnalysisRequestDTO request) {
//        try {
//            String[] fileIds = request.getFileIds();
//            String diseaseClass = request.getDiseaseClass();
//            int institutionId = request.getInstitutionId();
//            log.info("Analyzing data for file IDs: {}, diseaseClass: {}, institutionId: {}", Arrays.toString(fileIds), diseaseClass, institutionId);
//            List<Map<String, String>> dataList = dataAnalysisService.analyzeData(fileIds, diseaseClass, institutionId);
//            return ResponseEntity.ok(Map.of("data", dataList));
//        } catch (IOException e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("데이터 분석 중 오류가 발생했습니다.");
//        } catch (ExecutionException | InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//    }

    /**
     * 폴더 경로를 통한 CRF 분석
     * @param request
     * @return
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeData(@RequestBody AnalysisRequestDTO request) {
        try {
            String diseaseClass = request.getDiseaseClass();
            int institutionId = request.getInstitutionId();
            log.info("Analyzing data for file IDs: {}, diseaseClass: {}, institutionId: {}", folderPath, diseaseClass, institutionId);
            List<Map<String, String>> dataList = analyzeDataService.analyzeFolderData(folderPath, diseaseClass, institutionId);
            return ResponseEntity.ok(Map.of("data", dataList));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("데이터 분석 중 오류가 발생했습니다.");
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // 동적 필터링을 지원하는 새 API
    @PostMapping("/analyze-filters")
    public ResponseEntity<?> analyzeDataWithFilters(@RequestBody Map<String, Object> filterRequest) {
        try {
            // fileIds 추출
            log.info("Analyzing data with filters: {}", filterRequest);
            List<String> fileIdsList = (List<String>) filterRequest.get("fileIds");
            log.info("Analyzing data : {}", fileIdsList);
            String[] fileIds = fileIdsList.toArray(new String[0]);

            // 필터 조건 추출 (INSTITUTION_ID, P_GENDER 등)
            Map<String, String> filters = new HashMap<>();
            filterRequest.forEach((key, value) -> {
                if (!key.equals("fileIds") && !key.equals("header")) {  // fileIds와 header는 제외
                    filters.put(key, value.toString());
                }
            });

            // header 값 추출
            List<String> headers = (List<String>) filterRequest.get("header");
            log.info("Analyzing data with filters for file IDs: {}, filters: {}, headers: {}", Arrays.toString(fileIds), filters, headers);

            // 동적 필터링과 헤더 필터링을 수행하고 List<Map<String, Object>> 반환
            List<Map<String, Object>> filteredDataList = analyzeDataService.analyzeDataWithFilters(fileIds, filters, headers);

            // 변환된 List를 클라이언트에 반환
            return ResponseEntity.ok(filteredDataList);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("데이터 분석 중 오류가 발생했습니다.");
        }
    }



}
