package com.fas.dentistry_data_analysis.util;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.HttpStatus;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WebDavClient {

    public static void main(String[] args) {
        // WebDAV 서버 URL 및 경로
        String baseUrl = "http://202.86.11.27:5000/webdav";  // WebDAV 기본 경로
        String folderPath = "치의학데이터 과제 데이터 수집/내부 데이터"; // 실제 폴더 이름
        String serverUrl = baseUrl + "/" + encodePath(folderPath); // URL 인코딩된 경로
        String username = "dent_fas";  // Synology NAS 사용자 계정
        String password = "dent_fas123";  // Synology NAS 비밀번호

        System.out.println("WebDAV Client 실행 시작...");
        System.out.println("서버 URL: " + serverUrl);
        System.out.println("사용자 이름: " + username);
        System.out.println("비밀번호: " + (password != null ? "********" : "null"));

        HttpClient client = new HttpClient();

        // 인증 정보 설정
        client.getState().setCredentials(null, null, new UsernamePasswordCredentials(username, password));

        PropFindMethod method = null;

        try {
            // WebDAV 요청 생성
            method = new PropFindMethod(serverUrl);
            method.addRequestHeader("Depth", "1"); // 하위 디렉토리까지 조회

            System.out.println("HTTP 요청 실행 중...");
            int statusCode = client.executeMethod(method);

            // HTTP 응답 코드 확인
            System.out.println("HTTP 응답 코드: " + statusCode);

            if (statusCode == HttpStatus.SC_MULTI_STATUS) { // 207: WebDAV에서 여러 상태 응답
                MultiStatus multiStatus = method.getResponseBodyAsMultiStatus();
                MultiStatusResponse[] responses = multiStatus.getResponses();

                System.out.println("응답 받은 파일/폴더 목록:");
                for (MultiStatusResponse res : responses) {
                    System.out.println("- 파일 경로: " + res.getHref());
                }
            } else {
                System.out.println("에러 발생! HTTP 상태 코드: " + statusCode);
                System.out.println("응답 메시지: " + method.getResponseBodyAsString());
                System.out.println("요청 URI: " + method.getURI());
                System.out.println("요청 헤더: ");
                for (org.apache.commons.httpclient.Header header : method.getRequestHeaders()) {
                    System.out.println(" - " + header.getName() + ": " + header.getValue());
                }
            }
        } catch (IOException | DavException e) {
            System.out.println("WebDAV 요청 실패! URL 또는 권한을 확인하세요.");
            e.printStackTrace();
        } finally {
            if (method != null) {
                method.releaseConnection(); // 연결 해제
            }
            System.out.println("WebDAV Client 종료.");
        }
    }

    // 경로를 URL 인코딩
    private static String encodePath(String path) {
        try {
            return URLEncoder.encode(path, StandardCharsets.UTF_8.toString()).replace("+", "%20");
        } catch (Exception e) {
            throw new RuntimeException("URL 인코딩 실패", e);
        }
    }
}
