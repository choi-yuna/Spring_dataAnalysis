package com.fas.dentistry_data_analysis.controller;

import com.fas.dentistry_data_analysis.service.ExcelUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    @PostMapping("/upload-zip")
    public ResponseEntity<?> uploadZipFile(@RequestParam("file") MultipartFile file) {
        try {
            List<Map<String, String>> dataList = excelUploadService.processZipFile(file);

            // dataList를 'data' 키로 감싸서 반환
            Map<String, Object> outputData = Map.of("data", dataList);

            return ResponseEntity.ok(outputData);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ZIP 파일 처리 중 오류가 발생했습니다.");
        }
    }
}
