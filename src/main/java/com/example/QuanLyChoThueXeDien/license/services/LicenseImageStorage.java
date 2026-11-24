package com.example.QuanLyChoThueXeDien.license.services;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Service
public class LicenseImageStorage {
    // Private directory, deliberately not exposed by Spring's static resource handler.
    private final Path root = Path.of("uploads", "licenses").toAbsolutePath().normalize();
    public String save(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("Vui lòng cung cấp ảnh GPLX.");
        if (file.getSize() > 1024 * 1024)
            throw new IllegalArgumentException("Ảnh GPLX tối đa 1 MB.");
        try {
            byte[] data = file.getBytes();
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext())
                    throw new IllegalArgumentException("Ảnh phải là PNG hoặc JPEG hợp lệ.");
                var reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
                        throw new IllegalArgumentException("Chỉ hỗ trợ ảnh PNG hoặc JPEG.");
                    reader.setInput(input);
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 12_000_000)
                        throw new IllegalArgumentException("Ảnh có kích thước quá lớn; vui lòng chọn ảnh nhỏ hơn.");
                    if (reader.read(0) == null)
                        throw new IllegalArgumentException("Không đọc được ảnh GPLX.");
                    return restore(data, format.equalsIgnoreCase("png"));
                } finally
                {
                    reader.dispose();
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Không thể đọc hoặc lưu ảnh GPLX. Vui lòng thử lại.", e);
        }
    }


    public String restore(byte[] data, boolean png) {
        String name = UUID.randomUUID() + (png ? ".png" : ".jpg");
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(name), data, StandardOpenOption.CREATE_NEW);
            return name;
        } catch (IOException e) {
            throw new IllegalArgumentException("Không thể lưu ảnh GPLX. Vui lòng thử lại.", e);
        }
    }


    public byte[] read(String name) {
        try {
            return Files.readAllBytes(resolve(name));
        }
        catch (IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy ảnh GPLX");
        }
    }


    public void delete(String name) {
        if (name == null || !name.matches("[0-9a-f-]{36}\\.(png|jpg)"))
            return;
        try {
            Files.deleteIfExists(resolve(name));
        }
        catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Không thể dọn ảnh GPLX cũ", e);
        }
    }


    private Path resolve(String name) {
        if (name == null || !name.matches("[0-9a-f-]{36}\\.(png|jpg)"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return root.resolve(name);
    }
}
