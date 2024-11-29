package com.fas.dentistry_data_analysis.util;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import java.io.IOException;

public class SFTPConnectionWrapper implements AutoCloseable {
    private final Session session;
    private final ChannelSftp channelSftp;

    // 생성자에서 Session과 ChannelSftp를 초기화
    public SFTPConnectionWrapper(String host, String user, String password, int port) throws Exception {
        this.session = SFTPClient.createSession(host, user, password, port);  // Session 생성
        this.channelSftp = SFTPClient.createSftpChannel(session);  // ChannelSftp 생성
    }

    // Session과 ChannelSftp 객체를 반환하는 getter 메서드
    public Session getSession() {
        return session;
    }

    public ChannelSftp getChannelSftp() {
        return channelSftp;
    }

    // AutoCloseable을 구현하여 Session과 ChannelSftp를 종료할 수 있게 함
    @Override
    public void close() {
        if (channelSftp != null) {
            channelSftp.disconnect();  // ChannelSftp 종료
        }
        if (session != null) {
            session.disconnect();  // Session 종료
        }
    }
}
