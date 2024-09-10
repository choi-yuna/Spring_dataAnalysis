package com.fas.dentistry_data_analysis.controller;

import com.fas.dentistry_data_analysis.DTO.AnalysisRequestDTO;
import com.fas.dentistry_data_analysis.service.ExcelUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class ExcelUploadController {

    private final ExcelUploadService excelUploadService;

    public ExcelUploadController(ExcelUploadService excelUploadService) {
        this.excelUploadService = excelUploadService;
    }

    // 기존 다중 파일 업로드 API
    @PostMapping("/upload-folder")
    public ResponseEntity<?> uploadExcelFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            List<String> fileIds = new ArrayList<>();
            for (MultipartFile file : files) {
                if (!file.getOriginalFilename().endsWith(".xlsx")) {
                    return ResponseEntity.badRequest().body("엑셀 파일(.xlsx)만 업로드 가능합니다.");
                }
                String fileId = excelUploadService.storeFile(file);
                fileIds.add(fileId);
                log.info("Uploaded file with ID: {}", fileId);
            }
            return ResponseEntity.ok(Map.of("fileIds", fileIds));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("엑셀 파일 처리 중 오류가 발생했습니다.");
        }
    }

    // 기존 분석 API
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeData(@RequestBody AnalysisRequestDTO request) {
        try {
            String[] fileIds = request.getFileIds();
            String diseaseClass = request.getDiseaseClass();
            int institutionId = request.getInstitutionId();
            log.info("Analyzing data for file IDs: {}, diseaseClass: {}, institutionId: {}", Arrays.toString(fileIds), diseaseClass, institutionId);
            List<Map<String, String>> dataList = excelUploadService.analyzeData(fileIds, diseaseClass, institutionId);
            return ResponseEntity.ok(Map.of("data", dataList));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("데이터 분석 중 오류가 발생했습니다.");
        }
    }

    // 동적 필터링을 지원하는 새 API
    @PostMapping("/analyze-filters")
    public ResponseEntity<?> analyzeDataWithFilters(@RequestBody Map<String, Object> filterRequest) {
        try {
            // fileIds를 먼저 추출
            log.info("Analyzing data with filters: {}", filterRequest);
            List<String> fileIdsList = (List<String>) filterRequest.get("fileIds");
            String[] fileIds = fileIdsList.toArray(new String[0]);

            // 필터 조건 추출 (INSTITUTION_ID, P_GENDER 등)
            Map<String, String> filters = new HashMap<>();
            filterRequest.forEach((key, value) -> {
                if (!key.equals("fileIds")) {  // fileIds는 제외하고 나머지 필터 추가
                    filters.put(key, value.toString());
                }
            });

            log.info("Analyzing data with filters for file IDs: {}, filters: {}", Arrays.toString(fileIds), filters);

            // 동적 필터링을 통해 데이터 분석 수행
            List<Map<String, String>> filteredDataList = excelUploadService.analyzeDataWithFilters(fileIds, filters);

            return ResponseEntity.ok(Map.of("data", filteredDataList));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("데이터 분석 중 오류가 발생했습니다.");
        }
    }
}
