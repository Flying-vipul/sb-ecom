package com.ecommerce.project.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {


    String uploadImage(String path, MultipartFile file) throws IOException;

    java.util.List<String> uploadMultipleImages(String path, java.util.List<MultipartFile> files) throws IOException;
}
