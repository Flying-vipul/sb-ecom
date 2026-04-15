//package com.ecommerce.project.service;
//
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.UUID;
//
//@Service
//public class FileServiceImple implements FileService {
//    @Override
//    public String uploadImage(String path, MultipartFile file) throws IOException {
//        // File names of current / original file
//        String originalFileName = file.getOriginalFilename();
//
//        //Generate a unique file name
//        String randomId = UUID.randomUUID().toString();
//
//        //mat.jpg ---> 1234 --> 1234.jpg
//        String fileName = randomId.concat(originalFileName.substring(originalFileName.lastIndexOf('.')));
//        String filePath = path + File.separator+fileName;
//
//        // check if path is existing and create
//        File folder = new File(path);
//        if (!folder.exists()) {
//            folder.mkdir();
//        }
//
//        //Upload to server
//        Files.copy(file.getInputStream(), Paths.get(filePath));
//
//        //returning file Name
//        return fileName;
//    }
//}

package com.ecommerce.project.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class FileServiceImple implements FileService {

    @Autowired
    private Cloudinary cloudinary;

    @Override
    public String uploadImage(String path, MultipartFile file) throws IOException {

        // 1. We no longer need to create local folders or use UUIDs manually!
        // Cloudinary handles all the naming, hosting, and storage for us.

        // 2. Blast the file bytes straight to Cloudinary
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());

        // 3. Cloudinary gives us back a permanent, secure HTTPS URL.
        // We return this URL so your ProductController can save it to the database!
        return uploadResult.get("secure_url").toString();
    }
}
