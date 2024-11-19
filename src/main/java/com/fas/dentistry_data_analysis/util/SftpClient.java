package com.fas.dentistry_data_analysis.util;

import com.jcraft.jsch.*;

import java.util.Vector;

public class SftpClient {

    public static void main(String[] args) {
        // SFTP 서버 정보
        String host = "202.86.11.27"; // Synology NAS IP
        int port = 22; // SFTP 포트 (기본값: 22)
        String username = "dent_fas"; // Synology NAS 사용자 계정
        String password = "dent_fas123"; // Synology NAS 비밀번호
        String remoteDirectory = "/치의학데이터 과제 데이터 수집/내부 데이터"; // NAS 폴더 경로

        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            // 세션 생성
            session = jsch.getSession(username, host, port);
            session.setPassword(password);

            // 호스트 키 확인 비활성화 (운영 환경에서는 "StrictHostKeyChecking" 설정 필요)
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            // 세션 연결
            System.out.println("SFTP 세션 연결 중...");
            session.connect();
            System.out.println("SFTP 세션 연결 성공!");

            // SFTP 채널 열기
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();
            System.out.println("SFTP 채널 연결 성공!");

            // 원격 디렉토리 변경
            channelSftp.cd(remoteDirectory);
            System.out.println("원격 디렉토리 변경: " + remoteDirectory);

            // 디렉토리 내용 가져오기
            Vector<ChannelSftp.LsEntry> files = channelSftp.ls(".");
            System.out.println("디렉토리 내 파일 목록:");
            for (ChannelSftp.LsEntry entry : files) {
                // 파일 또는 디렉토리 이름 출력
                System.out.println("- " + entry.getFilename());
            }

        } catch (JSchException | SftpException e) {
            // 예외 처리
            System.out.println("SFTP 요청 실패! URL 또는 권한을 확인하세요.");
            e.printStackTrace();
        } finally {
            // SFTP 채널 및 세션 종료
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
                System.out.println("SFTP 채널 연결 해제");
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
                System.out.println("SFTP 세션 연결 해제");
            }
        }
    }
}
