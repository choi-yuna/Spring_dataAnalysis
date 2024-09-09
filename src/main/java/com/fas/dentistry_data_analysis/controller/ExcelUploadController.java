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

    // 다중 파일 업로드 API (폴더 내 엑셀 파일 업로드 처리)
    @PostMapping("/upload-folder")
    public ResponseEntity<?> uploadExcelFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            // 파일 ID 리스트를 저장할 리스트 생성
            List<String> fileIds = new ArrayList<>();

            // 각 파일을 저장하고, 저장된 파일 ID 리스트에 추가
            for (MultipartFile file : files) {
                if (!file.getOriginalFilename().endsWith(".xlsx")) {
                    return ResponseEntity.badRequest().body("엑셀 파일(.xlsx)만 업로드 가능합니다.");
                }
                String fileId = excelUploadService.storeFile(file);
                fileIds.add(fileId);
                log.info("Uploaded file with ID: {}", fileId);
            }

            // 파일 ID 리스트를 클라이언트로 반환
            return ResponseEntity.ok(Map.of("fileIds", fileIds));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("엑셀 파일 처리 중 오류가 발생했습니다.");
        }
    }

    // 여러 파일 ID로 데이터 분석 API
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeData(@RequestBody AnalysisRequestDTO request) {
        try {
            String[] fileIds = request.getFileIds();  // 여러 파일 ID 받음
            String diseaseClass = request.getDiseaseClass();
            int institutionId = request.getInstitutionId();

            log.info("Analyzing data for file IDs: {}, diseaseClass: {}, institutionId: {}", Arrays.toString(fileIds), diseaseClass, institutionId);

            // 다중 파일 ID와 필터 조건(DISEASE_CLASS, INSTITUTION_ID)을 기반으로 데이터 분석 수행
            List<Map<String, String>> dataList = excelUploadService.analyzeData(fileIds, diseaseClass, institutionId);

            // 분석된 데이터를 반환
            return ResponseEntity.ok(Map.of("data", dataList));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("데이터 분석 중 오류가 발생했습니다.");
        }
    }
}
