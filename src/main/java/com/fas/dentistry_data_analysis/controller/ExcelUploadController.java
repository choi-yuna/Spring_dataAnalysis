package com.fas.dentistry_data_analysis.controller;

import com.fas.dentistry_data_analysis.service.ExcelUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public ExcelUploadController(ExcelUploadService excelUploadService) {
        this.excelUploadService = excelUploadService;
    }

    // 기존 다중 파일 업로드 API
    @PostMapping("/upload-folder")
    public ResponseEntity<?> uploadExcelFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            List<String> fileIds = new ArrayList<>();
            for (MultipartFile file : files) {
                if (!Objects.requireNonNull(file.getOriginalFilename()).endsWith(".xlsx")) {
                    return ResponseEntity.badRequest().body("엑셀 파일(.xlsx)만 업로드 가능합니다.");
                }
                String fileId = excelUploadService.storeFile(file);
                fileIds.add(fileId);
                log.info("Uploaded file with ID: {}", fileId);
            }
            return ResponseEntity.ok(Map.of("fileIds", fileIds));
        }
        catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("엑셀 파일 처리 중 오류가 발생했습니다.");
        }
    }

}
