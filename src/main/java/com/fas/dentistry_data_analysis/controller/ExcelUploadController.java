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
            // fileIds 추출
            log.info("Analyzing data with filters: {}", filterRequest);
            List<String> fileIdsList = (List<String>) filterRequest.get("fileIds");
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

            // 동적 필터링과 헤더 필터링을 수행하고 Map<String, List<Map<String, Object>>> 반환
            Map<String, List<Map<String, Object>>> filteredDataMap = excelUploadService.analyzeDataWithFilters(fileIds, filters, headers);

            // 변환된 데이터를 그대로 클라이언트에 반환
            return ResponseEntity.ok(Map.of("data", filteredDataMap));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("데이터 분석 중 오류가 발생했습니다.");
        }
    }



}
