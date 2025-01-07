package com.fas.dentistry_data_analysis.dataAnlaysis.service;


import com.fas.dentistry_data_analysis.dataAnlaysis.util.json.JsonHeaderMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
public class JsonFileProcessorServiceImpl implements JsonFileProcessor {

    @Override
    // JSON 파일 처리 메소드
    public List<Map<String, Map<String, String>>> processServerJsonFile(
            File jsonFile,
            String diseaseClass,
            int institutionId) throws IOException {

        List<Map<String, Map<String, String>>> dataList = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try (InputStream inputStream = new FileInputStream(jsonFile)) {
            // JSON 파일 파싱
            JsonNode rootNode = objectMapper.readTree(inputStream);

            // JSON 배열인지 확인
            if (!rootNode.isArray()) {
                throw new IllegalArgumentException("JSON 파일이 배열 형식이어야 합니다.");
            }

            for (JsonNode recordNode : rootNode) {
                // JSON 데이터의 DISEASE_CLASS 추출
                String diseaseClassValue = diseaseClass;

                // 헤더 매핑 가져오기
                Map<String, List<String>> headers = JsonHeaderMapping.getHeadersForJson(diseaseClassValue);
                if (headers == null) {
                    log.warn("헤더 매핑을 찾을 수 없습니다: " + diseaseClassValue);
                    continue;
                }

                List<String> requiredHeaders = headers.get("required");
                List<String> optionalHeaders = headers.get("optional");


                // 필수 데이터 추출
                Map<String, String> requiredData = new LinkedHashMap<>();
                for (String header : requiredHeaders) {
                    JsonNode valueNode = findValueInSections(recordNode, header);
                    if (valueNode == null || valueNode.isNull()) {
                        log.warn("필수 헤더가 누락되었습니다: " + header);
                        requiredData.put(header, "N/A"); // 누락된 필수 값은 기본값 처리
                    } else {
                        requiredData.put(header, valueNode.asText());
                    }
                }

                // 선택 데이터 추출
                Map<String, String> optionalData = new LinkedHashMap<>();
                for (String header : optionalHeaders) {
                    JsonNode valueNode = findValueInSections(recordNode, header);
                    if (valueNode != null && !valueNode.isNull()) {
                        optionalData.put(header, valueNode.asText());
                    }
                }

                // 결과 데이터 구성
                Map<String, Map<String, String>> rowData = new HashMap<>();
                Map<String, String> combinedRequiredData = new HashMap<>();
                combinedRequiredData.put("disease", diseaseClass);
                combinedRequiredData.putAll(requiredData); // 기존 requiredData 병합
                rowData.put("required", combinedRequiredData);
                rowData.put("optional", optionalData);
                dataList.add(rowData);
            }
        } catch (IOException e) {
            log.error("JSON 파일 처리 중 오류 발생: {}", e.getMessage());
            throw e;
        }

        return dataList; // 최종 데이터 반환
    }

    /**
     * JSON의 여러 섹션에서 특정 키를 찾아 값을 반환.
     * @param recordNode JSON 데이터 레코드 노드
     * @param key 찾을 키
     * @return 값 (없으면 null)
     */

    private JsonNode findValueInSections(JsonNode recordNode, String key) {
        // 공백 제거된 key 생성
        String sanitizedKey = key.replaceAll("\\s+", "");

        // JSON 최상위에서 값 검색
        Iterator<String> rootFieldNames = recordNode.fieldNames();
        while (rootFieldNames.hasNext()) {
            String fieldName = rootFieldNames.next();
            if (fieldName.replaceAll("\\s+", "").equals(sanitizedKey)) {
                return recordNode.get(fieldName);
            }
        }

        // JSON 섹션에서 값 검색
        Iterator<Map.Entry<String, JsonNode>> fields = recordNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode section = field.getValue();

            if (section.isArray()) {
                for (JsonNode item : section) {
                    Iterator<String> itemFieldNames = item.fieldNames();
                    while (itemFieldNames.hasNext()) {
                        String itemFieldName = itemFieldNames.next();
                        if (itemFieldName.replaceAll("\\s+", "").equals(sanitizedKey)) {
                            return item.get(itemFieldName);
                        }
                    }
                }
            } else {
                Iterator<String> sectionFieldNames = section.fieldNames();
                while (sectionFieldNames.hasNext()) {
                    String sectionFieldName = sectionFieldNames.next();
                    if (sectionFieldName.replaceAll("\\s+", "").equals(sanitizedKey)) {
                        return section.get(sectionFieldName);
                    }
                }
            }
        }

        // 값을 찾을 수 없으면 null 반환
        return null;
    }


    @Override
    public List<Map<String, Map<String, String>>> processJsonFile(File file, String diseaseClass, int institutionId) throws IOException {
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".json")) {
            return processServerJsonFile(file, diseaseClass, institutionId);
        } else {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + fileName);
        }
    }
}
