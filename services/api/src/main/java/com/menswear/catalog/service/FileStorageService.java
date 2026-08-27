package com.menswear.catalog.service;

import com.menswear.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );

    private final Path root;
    private final String publicBaseUrl;

    public FileStorageService(
            @Value("${menswear.upload.dir:uploads}") String uploadDir,
            @Value("${menswear.upload.public-base-url:http://localhost:8080/media}") String publicBaseUrl
    ) throws IOException {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
        Files.createDirectories(this.root.resolve("products"));
    }

    public String storeProductImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Empty image file");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED.contains(contentType)) {
            throw new BadRequestException("Only JPG, PNG, WEBP, GIF images are allowed");
        }

        String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String ext = extension(original, contentType);
        String filename = UUID.randomUUID() + ext;
        Path target = root.resolve("products").resolve(filename);

        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new BadRequestException("Failed to save image: " + e.getMessage());
        }

        return publicBaseUrl + "/products/" + filename;
    }

    private String extension(String original, String contentType) {
        int dot = original.lastIndexOf('.');
        if (dot > -1 && dot < original.length() - 1) {
            return original.substring(dot).toLowerCase();
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }
}
