package com.fas.dentistry_data_analysis.util;

import com.jcraft.jsch.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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
    public static void deleteFile(ChannelSftp channelSftp, String filePath) throws SftpException {
        channelSftp.rm(filePath);
    }


    // SFTP 서버에서 특정 폴더의 파일 목록을 가져오는 메소드
    // SFTP 서버에서 특정 폴더의 파일 목록을 가져오는 메소드
    public static List<ChannelSftp.LsEntry> listFiles(ChannelSftp channelSftp, String folderPath) throws SftpException {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new IllegalArgumentException("Folder path cannot be null or empty.");
        }
        @SuppressWarnings("unchecked")
        Vector<ChannelSftp.LsEntry> vector = channelSftp.ls(folderPath);

        // Vector를 ArrayList로 변환하여 반환
        return new ArrayList<>(vector);
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

    // SFTP 서버에 텍스트 파일 업로드 메서드
    public static void uploadTextFile(ChannelSftp channelSftp, String folderPath, String fileName, String content) throws SftpException {
        if (folderPath == null || fileName == null || folderPath.isEmpty() || fileName.isEmpty()) {
            throw new IllegalArgumentException("Folder path or file name cannot be null or empty.");
        }

        if (content == null) {
            content = ""; // 내용이 null인 경우 빈 문자열로 처리
        }

        // 텍스트 내용을 InputStream으로 변환
        try (InputStream inputStream = new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            channelSftp.put(inputStream, folderPath + "/" + fileName);
        } catch (Exception e) {
            throw new SftpException(ChannelSftp.SSH_FX_FAILURE, "Failed to upload text file to SFTP", e);
        }
    }
/**
 * 연결테스트 로직
 */
//    public static void main(String[] args) {
//   String host  = "210.126.75.11";  // SFTP 서버 IP
//     int port  = 2024;  // SFTP 포트
//       String user  = "master01";  // 사용자 계정
//       String password  = "Master01!!!";  // 비밀번호
//
//        Session session = null;
//        ChannelSftp channelSftp = null;
//
//        try {
//            // 세션 생성
//            session = SFTPClient.createSession(host, user, password, port);
//            System.out.println("SFTP Session 연결 성공!");
//
//            // 채널 생성
//            channelSftp = SFTPClient.createSftpChannel(session);
//            System.out.println("SFTP Channel 연결 성공!");
//
//            // 연결 성공 여부 확인
//            if (channelSftp.isConnected()) {
//                System.out.println("SFTP 연결이 정상적으로 이루어졌습니다.");
//            } else {
//                System.out.println("SFTP 연결에 실패했습니다.");
//            }
//        } catch (Exception e) {
//            System.err.println("SFTP 연결 중 오류 발생: " + e.getMessage());
//            e.printStackTrace();
//        } finally {
//            // 연결 종료
//            SFTPClient.disconnect(session, channelSftp);
//            System.out.println("SFTP 연결 종료");
//        }
//    }
}

