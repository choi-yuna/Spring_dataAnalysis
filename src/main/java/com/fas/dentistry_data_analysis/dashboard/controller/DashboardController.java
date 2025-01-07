package com.fas.dentistry_data_analysis.dashboard.controller;

import com.fas.dentistry_data_analysis.dashboard.Service.AnalyzeBoardServiceImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;


    @Slf4j
    @RestController
    @RequestMapping("/api")
    public class DashboardController {

        // private final String folderPath = "/치의학데이터 과제 데이터 수집/내부 데이터/단국대/골수염";
        private final String folderPath = "/내부 데이터";


        private final AnalyzeBoardServiceImpl analyzeBoardService;

        @Autowired
        public DashboardController(AnalyzeBoardServiceImpl analyzeBoardService) {
            this.analyzeBoardService = analyzeBoardService;
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



}
