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
        Map<String, Integer> result = analyzeBoardService.processFilesInFolder(folderPath);

        // 결과가 null이 아니고 "전체파일"과 "오류파일"이 존재하는지 확인
        assertNotNull(result);
        assertTrue(result.containsKey("전체파일"));
        assertTrue(result.containsKey("오류파일"));

        // 전체 파일과 오류 파일의 갯수가 0 이상인지 확인
        assertTrue(result.get("전체파일") >= 0);
        assertTrue(result.get("오류파일") >= 0);

        // 테스트로 출력 확인
        System.out.println("전체파일 갯수: " + result.get("전체파일"));
        System.out.println("오류파일 갯수: " + result.get("오류파일"));
        System.out.println("라벨링: " + result.get("라벨링"));
        System.out.println("1차검수 갯수: " + result.get("1차검수"));
        System.out.println("2차검수 갯수: " + result.get("2차검수"));
    }
}
