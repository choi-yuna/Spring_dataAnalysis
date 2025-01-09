package com.fas.dentistry_data_analysis.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Getter
public class StorageConfig {

    @Value("${data.storage.path}")
    private String storagePath;

    @Value("${data.storage.folderPath}")
    private String folderPath;

    public String getDecodedFolderPath() {
        // UTF-8로 강제 변환
        return new String(folderPath.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

}
