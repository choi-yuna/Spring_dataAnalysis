package com.fas.dentistry_data_analysis.dashboard.Service;

import com.fas.dentistry_data_analysis.dataAnlaysis.service.FolderFileCacheManager;
import com.fas.dentistry_data_analysis.common.util.sftp.SFTPClient;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SftpService {
    private final FolderFileCacheManager folderFileCacheManager;

    public SftpService(FolderFileCacheManager folderFileCacheManager) {
        this.folderFileCacheManager = folderFileCacheManager;
    }


    public boolean checkFileExistsInSFTP(ChannelSftp channelSftp, String folderPath, String fileName, String subFolder) throws SftpException {
        // 폴더와 서브폴더 경로 결합
        String targetPath = folderPath + subFolder;

        // 캐시에서 해당 폴더의 파일 목록을 가져옴
        Set<String> cachedFiles = folderFileCacheManager.computeIfAbsent(targetPath, path -> {
            try {
                // SFTP에서 파일 목록 가져오기
                List<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, path);

                // 파일 이름만 추출하여 Set으로 변환
                return files.stream()
                        .map(ChannelSftp.LsEntry::getFilename)
                        .collect(Collectors.toSet());
            } catch (SftpException e) {
                // 파일 목록 조회 실패 시 빈 Set 반환
                log.error("Failed to list files in folder: {}", path, e);
                return Collections.emptySet();
            }
        });


        // 캐시된 파일 목록에서 해당 파일이 있는지 확인
        return cachedFiles.contains(fileName);
    }

    public boolean checkFileExistsInSFTPForImageId(ChannelSftp channelSftp, String folderPath, String imageId) throws SftpException {
        // 주어진 경로에 대한 캐시를 초기화하거나 가져옴
        Set<String> cachedFolders = folderFileCacheManager.computeIfAbsent(folderPath, path -> {
            try {
                // SFTP에서 폴더 목록 가져오기
                List<ChannelSftp.LsEntry> entries = SFTPClient.listFiles(channelSftp, path);

                return entries.stream()
                        .filter(entry -> entry.getAttrs() != null && entry.getAttrs().isDir()) // 디렉토리만 필터링
                        .map(ChannelSftp.LsEntry::getFilename) // 폴더 이름만 추출
                        .collect(Collectors.toSet());
            } catch (SftpException e) {
                // 에러 발생 시 빈 Set 반환
                log.error("Error accessing path: {}", path, e);
                return Collections.emptySet();
            }
        });

        // 폴더 이름 중 imageId를 포함하는 폴더가 있는지 확인
        return cachedFolders.stream().anyMatch(folderName -> folderName.contains(imageId));
    }


    public int countFilteredFoldersInPath(ChannelSftp channelSftp, String folderPath, String keyword) throws SftpException {
        // 주어진 경로에 대한 캐시를 초기화하거나 가져옴
        folderFileCacheManager.clearCache();
        Set<String> cachedFolders = folderFileCacheManager.computeIfAbsent(folderPath, path -> {
            try {
                // SFTP에서 디렉토리 목록 가져오기
                List<ChannelSftp.LsEntry> entries = SFTPClient.listFiles(channelSftp, path);

                return entries.stream()
                        .filter(entry -> entry.getAttrs() != null && entry.getAttrs().isDir()) // 디렉토리만 필터링
                        .map(ChannelSftp.LsEntry::getFilename) // 디렉토리 이름 추출
                        .collect(Collectors.toSet());
            } catch (SftpException e) {
                // 에러 발생 시 로그를 남기고 빈 Set 반환
                log.error("Failed to list folders in path: {}", path, e);
                return Collections.emptySet();
            }
        });


        // 필터링된 폴더 갯수 반환 (특정 키워드가 포함된 폴더만 카운트)
        return (int) cachedFolders.stream()
                .filter(folderName -> folderName.contains(keyword)) // 원하는 폴더 이름 필터링
                .count();
    }

    public boolean checkLabellingFileExistsInSFTPForImageId(ChannelSftp channelSftp, String folderPath, String imageId) {
        // 캐시에서 파일/폴더 세트를 가져오거나 초기화
        Set<String> filesInCache = folderFileCacheManager.computeIfAbsent(folderPath, path -> {
            try {
                // SFTP에서 파일 목록 가져오기
                List<ChannelSftp.LsEntry> files = SFTPClient.listFiles(channelSftp, path);

                return files.stream()
                        .map(ChannelSftp.LsEntry::getFilename) // 파일 이름 추출
                        .collect(Collectors.toSet());
            } catch (SftpException e) {
                // 에러 발생 시 로그를 남기고 빈 Set 반환
                log.error("Failed to list files in folder: {}", path, e);
                return Collections.emptySet();
            }
        });

        // 캐시에서 imageId를 포함하는 파일/폴더 이름이 있는지 확인
        return filesInCache.stream().anyMatch(name -> name.contains(imageId));
    }

}
