package com.fas.dentistry_data_analysis;

import com.fas.dentistry_data_analysis.service.AnalyzeBoardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzeBoardServiceImplTest {

    private AnalyzeBoardServiceImpl analyzeBoardService;

    @BeforeEach
    void setUp() {
        analyzeBoardService = new AnalyzeBoardServiceImpl();
    }

    @Test
    void testProcessFilesInFolder() throws IOException {
        // 테스트용 폴더 경로 (테스트 디렉토리에 맞게 경로 수정 필요)
        String folderPath = "C:/Users/fasol/OneDrive/바탕 화면/BRM 701~800";

        // processFilesInFolder 호출하여 결과를 가져옴
        Map<String, Object> result = analyzeBoardService.processFilesInFolder(folderPath);

        // 결과가 null이 아니고 "전체파일"과 "오류파일"이 존재하는지 확인
        assertNotNull(result);
        assertTrue(result.containsKey("전체파일"));
        assertTrue(result.containsKey("오류파일"));

        // 전체 파일과 오류 파일의 갯수가 0 이상인지 확인
        assertTrue((Integer) result.get("전체파일") >= 0);
        assertTrue((Integer) result.get("오류파일") >= 0);

        // "라벨링", "1차검수", "2차검수" 값 확인
        assertTrue(result.containsKey("라벨링"));
        assertTrue(result.containsKey("1차검수"));
        assertTrue(result.containsKey("2차검수"));

        // 질환별, 대학별 데이터를 포함하는지 확인
        assertTrue(result.containsKey("질환별"));
        assertTrue(result.containsKey("대학별"));

        // 질환별, 대학별 항목들이 Map 형태로 되어 있는지 확인
        Map<String, Map<String, Integer>> diseaseMap = (Map<String, Map<String, Integer>>) result.get("질환별");
        Map<String, Map<String, Integer>> institutionMap = (Map<String, Map<String, Integer>>) result.get("대학별");

        assertNotNull(diseaseMap);
        assertNotNull(institutionMap);
        // 질환별 및 대학별 출력 확인
        System.out.println("질환별: " + diseaseMap);
        System.out.println("대학별: " + institutionMap);
    }
}
