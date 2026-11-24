package com.example.QuanLyChoThueXeDien.Cloudinary;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudinaryLicenseImagesTests {
    @Test
    void uploadsDownloadsAndDeletesOnlyAuthenticatedImages() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var images = new CloudinaryLicenseImages("test-cloud", "test-key", "test-secret", builder);
        String reference = "cloudinary:8aa26275-4a03-45f0-bd8e-dce4c3579bf3.png";
        String publicId = "driving-licenses/8aa26275-4a03-45f0-bd8e-dce4c3579bf3";
        String endpoint = "https://api.cloudinary.com/v1_1/test-cloud/image";
        byte[] image = {1, 2, 3};

        server.expect(requestTo(endpoint + "/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("type=authenticated")))
                .andExpect(content().string(containsString("file=data%3Aimage%2Fpng%3Bbase64%2CAQID")))
                .andExpect(content().string(containsString("signature=")))
                .andRespond(withSuccess("""
                    {"public_id":"%s","type":"authenticated","format":"png"}
                    """.formatted(publicId), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(endpoint + "/download?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("type", "authenticated"))
                .andExpect(queryParam("public_id", publicId))
                .andExpect(queryParam("format", "png"))
                .andExpect(queryParam("api_key", "test-key"))
                .andRespond(withSuccess(image, MediaType.IMAGE_PNG));
        server.expect(requestTo(endpoint + "/destroy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("type=authenticated")))
                .andExpect(content().string(containsString("invalidate=true")))
                .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));

        images.upload(reference, image);
        assertArrayEquals(image, images.read(reference));
        images.delete(reference);
        server.verify();
    }

    @Test
    void approvalRequiresCloudName() {
        var images = new CloudinaryLicenseImages("", "test-key", "test-secret", RestClient.builder());
        assertThrows(IllegalArgumentException.class, () -> images.newReference("license.png"));
    }
}
