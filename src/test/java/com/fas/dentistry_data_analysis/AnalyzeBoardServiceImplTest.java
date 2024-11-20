package com.fas.dentistry_data_analysis;

import com.fas.dentistry_data_analysis.service.AnalyzeBoardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzeBoardServiceImplTest {

    private AnalyzeBoardServiceImpl analyzeBoardService;

    @BeforeEach
    void setUp() {
        analyzeBoardService = new AnalyzeBoardServiceImpl();
    }

    @Test
    void testProcessFilesInFolder() throws Exception {
        // 테스트용 폴더 경로 (테스트 디렉토리에 맞게 경로 수정 필요)
        String folderPath = "C:/Users/fasol/OneDrive/바탕 화면/BRM 701~800";

        // processFilesInFolder 호출하여 결과를 가져옴
        Map<String, Object> result = analyzeBoardService.processFilesInFolder(folderPath);

        // 결과가 null이 아니고 질환별, 기관별 상태 정보가 포함되어 있는지 확인


        System.out.println(result);
    }
}
