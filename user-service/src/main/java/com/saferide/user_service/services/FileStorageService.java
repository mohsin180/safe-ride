package com.saferide.user_service.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {
    @Value("${file.upload-dir}")
    private String uploadDir;

    public String storeProfileImage(MultipartFile file, UUID userId) {
        try {
            Files.createDirectories(Paths.get(uploadDir));

            String filename = userId + "_" + System.currentTimeMillis()
                    + "_" + file.getOriginalFilename();

            Path filePath = Paths.get(uploadDir).resolve(filename);

            Files.copy(
                    file.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return "/api/files/profiles/" + filename;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store profile image", e);
        }
    }
}
