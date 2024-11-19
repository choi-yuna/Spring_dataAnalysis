package com.fas.dentistry_data_analysis.util;

import org.apache.http.client.methods.HttpUriRequest;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;

import java.io.IOException;

public class WebDavClient {

    public static void main(String[] args) {
        // WebDAV 서버 URL 및 인증 정보
        String serverUrl = "http://202.86.11.27:5000/volume1";  // 원하는 폴더 경로
        String username = "dent_fas";  // Synology NAS 사용자 계정
        String password = "dent_fas123";  // Synology NAS 비밀번호

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            // PropFindMethod로 WebDAV 요청을 보냄 (폴더 및 하위 파일 목록 요청)
            PropFindMethod method = new PropFindMethod(serverUrl);  // DEPTH_1은 하위 폴더까지 조회
            method.setDoAuthentication(true); // 인증 설정
            method.setRequestHeader("Authorization", "Basic " + getBase64EncodedCredentials(username, password));

            // HTTP 요청 실행
            httpClient.execute((HttpUriRequest) method);

            // 서버 응답 확인
            if (method.getStatusCode() == 207) { // 207: WebDAV에서 여러 상태 응답
                MultiStatus multiStatus = method.getResponseBodyAsMultiStatus();
                MultiStatusResponse[] responses = multiStatus.getResponses();

                // 폴더 내 파일 목록 출력
                for (MultiStatusResponse response : responses) {
                    System.out.println("파일 경로: " + response.getHref());
                    // 추가적인 메타데이터가 필요하면 출력 가능
                }
            } else {
                System.out.println("에러: " + method.getStatusCode());
            }

        } catch (IOException | DavException e) {
            e.printStackTrace();
        }
    }

    // 사용자 인증을 Base64로 인코딩하여 Authorization 헤더에 추가
    private static String getBase64EncodedCredentials(String username, String password) {
        String credentials = username + ":" + password;
        return java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}
