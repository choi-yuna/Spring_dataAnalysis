package com.fas.dentistry_data_analysis.dashboard.util;

import com.jcraft.jsch.ChannelSftp;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FolderFileCacheManager {
    private final Map<String, Set<String>> folderFileCache = new ConcurrentHashMap<>();

    // 캐시에서 데이터를 가져오거나 없을 경우 Supplier를 통해 새로 생성
    public Set<String> computeIfAbsent(String path, FileFetcher fetcher) {
        return folderFileCache.computeIfAbsent(path, key -> {
            try {
                return fetcher.fetchFiles(key);
            } catch (Exception e) {
                return Collections.emptySet(); // 예외 발생 시 빈 Set 반환
            }
        });
    }

    // 특정 경로에 캐시된 데이터 가져오기
    public Set<String> getCachedFiles(String path) {
        return folderFileCache.getOrDefault(path, Collections.emptySet());
    }

    // 캐시 초기화
    public void clearCache() {
        folderFileCache.clear();
    }

    private boolean isValidDirectory(ChannelSftp.LsEntry entry) {
        if (entry.getAttrs() == null || !entry.getAttrs().isDir()) {
            return false; // 디렉토리인지 확인
        }

        String filename = entry.getFilename();
        if (filename == null || filename.equals(".") || filename.equals("..")) {
            return false; // ".", ".." 폴더 제외
        }

        // 추가 필터링: 특정 패턴 제외 (예: ".json", ".xlsx")
        return !filename.startsWith(".") && !filename.endsWith(".json") && !filename.endsWith(".xlsx");
    }


    @FunctionalInterface
    public interface FileFetcher {
        Set<String> fetchFiles(String path) throws Exception;
    }
}
