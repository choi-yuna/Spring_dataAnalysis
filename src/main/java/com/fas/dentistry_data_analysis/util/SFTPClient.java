package com.fas.dentistry_data_analysis.util;

import com.jcraft.jsch.*;

import java.io.InputStream;
import java.util.Vector;

public class SFTPClient {

    private static JSch jsch = new JSch(); // JSch 객체를 재사용

    // SFTP 연결을 위한 세션 생성
    public static Session createSession(String host, String user, String password, int port) throws JSchException {
        Session session = jsch.getSession(user, host, port);
        session.setPassword(password);

        // SFTP 연결 시 기본적으로 호스트 검증을 하지 않음 (보안 취약점 유의)
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        return session;
    }

    // SFTP 채널 생성
    public static ChannelSftp createSftpChannel(Session session) throws JSchException {
        Channel channel = session.openChannel("sftp");
        channel.connect();

        return (ChannelSftp) channel;
    }

    // SFTP 서버에서 특정 폴더의 파일 목록을 가져오는 메소드
    public static Vector<ChannelSftp.LsEntry> listFiles(ChannelSftp channelSftp, String folderPath) throws SftpException {
        // 폴더 경로 확인 후, 존재하지 않으면 빈 벡터 반환
        if (folderPath == null || folderPath.isEmpty()) {
            throw new IllegalArgumentException("Folder path cannot be null or empty.");
        }
        return channelSftp.ls(folderPath);
    }

    // SFTP 서버에서 파일을 읽어오는 메소드
    public static InputStream readFile(ChannelSftp channelSftp, String folderPath, String fileName) throws SftpException {
        if (folderPath == null || fileName == null || folderPath.isEmpty() || fileName.isEmpty()) {
            throw new IllegalArgumentException("Folder path or file name cannot be null or empty.");
        }
        return channelSftp.get(folderPath + "/" + fileName);
    }

    // SFTP 서버에서 파일을 업로드하는 메소드
    public static void uploadFile(ChannelSftp channelSftp, String folderPath, String fileName, InputStream inputStream) throws SftpException {
        if (folderPath == null || fileName == null || folderPath.isEmpty() || fileName.isEmpty()) {
            throw new IllegalArgumentException("Folder path or file name cannot be null or empty.");
        }
        channelSftp.put(inputStream, folderPath + "/" + fileName);
    }

    // SFTP 연결 종료 메소드
    public static void disconnect(Session session, ChannelSftp channelSftp) {
        if (channelSftp != null && channelSftp.isConnected()) {
            channelSftp.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
