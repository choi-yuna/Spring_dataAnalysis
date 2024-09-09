package com.fas.dentistry_data_analysis.controller;


import com.fas.dentistry_data_analysis.service.ExcelUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ExcelAnalyzeController {

    private final ExcelUploadService excelUploadService;

    public ExcelAnalyzeController(ExcelUploadService excelUploadService) {
        this.excelUploadService = excelUploadService;
    }

//    @GetMapping("/diseases/{disease}")
//    public ResponseEntity<?> analyzeData(@PathVariable String disease) {
//
//        excelUploadService.analyzeData();
//
//        return ResponseEntity.ok().build();
//    }


}
