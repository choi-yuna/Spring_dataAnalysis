package com.fas.dentistry_data_analysis.config;


import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class SftpConfig {

    @Value("${sftp.server.host}")
    private String host;

    @Value("${sftp.server.port}")
    private int port;

    @Value("${sftp.server.user}")
    private String user;

    @Value("${sftp.server.password}")
    private String password;


}
