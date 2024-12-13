package com.fas.dentistry_data_analysis.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class StorageConfig {

    @Value("${data.storage.path}")
    private String storagePath;


}
