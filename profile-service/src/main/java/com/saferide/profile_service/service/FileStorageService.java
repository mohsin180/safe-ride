package com.saferide.profile_service.service;

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

    private static final String BASE_PATH = "uploads";

    public String storeImage(MultipartFile file, UUID userId, String type) {
        try {
            String folder = BASE_PATH + "/" + type + "/" + userId;
            Files.createDirectories(Paths.get(folder));

            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path path = Paths.get(folder, filename);

            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

            return "/" + folder + "/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Image upload failed");
        }
    }
}

