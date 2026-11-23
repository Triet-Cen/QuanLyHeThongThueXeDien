package com.example.QuanLyChoThueXeDien.license;
import com.example.QuanLyChoThueXeDien.license.services.LicenseImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class LicenseImageStorageTests {
    LicenseImageStorage storage = new LicenseImageStorage();
    @Test void requiresRealImageNotJustAnExtension() {
        assertThrows(IllegalArgumentException.class, () -> storage.save(null));
        assertThrows(IllegalArgumentException.class, () -> storage.save(new MockMultipartFile("image","license.png","image/png","not an image".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> storage.save(new MockMultipartFile("image","license.jpg","image/jpeg",new byte[1024*1024+1])));
    }
    @Test void storesPngWithGeneratedNameAndReadsBytes() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);
        String name = storage.save(new MockMultipartFile("image","../../license.png","image/png",out.toByteArray()));
        try {
            assertTrue(name.matches("[0-9a-f-]{36}\\.png"));
            assertArrayEquals(out.toByteArray(), storage.read(name));
        } finally { storage.delete(name); }
    }
    @Test void doesNotReadArbitraryPaths() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> storage.read("../../.env"));
    }
}
