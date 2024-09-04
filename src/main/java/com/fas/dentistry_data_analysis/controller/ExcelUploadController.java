package com.fas.dentistry_data_analysis.controller;

import com.fas.dentistry_data_analysis.DTO.AnalysisRequestDTO;
import com.fas.dentistry_data_analysis.service.ExcelUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExcelUploadController {

    private final ExcelUploadService excelUploadService;

    public ExcelUploadController(ExcelUploadService excelUploadService) {
        this.excelUploadService = excelUploadService;
    }

    // 파일 업로드 API
    @PostMapping("/upload-zip")
    public ResponseEntity<?> uploadZipFile(@RequestParam("file") MultipartFile file) {
        try {
            // 파일을 저장하고 고유한 파일 ID를 반환
            String fileId = excelUploadService.storeFile(file);

            // 파일 ID를 클라이언트로 반환
            return ResponseEntity.ok(Map.of("fileId", fileId));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ZIP 파일 처리 중 오류가 발생했습니다.");
        }
    }

    // 파일 ID로 데이터 분석 API
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeData(@RequestBody AnalysisRequestDTO request) {
        try {

            String fileId = request.getFileId();
            String diseaseClass = request.getDiseaseClass();
            int institutionId = request.getInstitutionId();
            // 파일 ID와 필터 조건(DISEASE_CLASS, INSTITUTION_ID)을 기반으로 데이터 분석 수행
            List<Map<String, String>> dataList = excelUploadService.analyzeData(fileId, diseaseClass, institutionId);

            // 분석된 데이터를 반환
            return ResponseEntity.ok(Map.of("data", dataList));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("데이터 분석 중 오류가 발생했습니다.");
        }
    }
}
