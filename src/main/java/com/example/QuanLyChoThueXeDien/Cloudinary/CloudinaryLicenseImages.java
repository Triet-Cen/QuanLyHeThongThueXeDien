package com.example.QuanLyChoThueXeDien.Cloudinary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class CloudinaryLicenseImages {
    private static final Logger logger = LoggerFactory.getLogger(CloudinaryLicenseImages.class);
    private static final String REFERENCE_PREFIX = "cloudinary:";
    private static final String ASSET_FOLDER = "driving-licenses";
    private static final String DELIVERY_TYPE = "authenticated";
    private static final Pattern REFERENCE = Pattern.compile(REFERENCE_PREFIX + "[0-9a-f-]{36}\\.(jpg|png)");
    private static final String UPLOAD_ERROR = "Không thể lưu ảnh GPLX lên Cloudinary. Vui lòng thử lại.";

    private final String apiKey;
    private final String apiSecret;
    private final boolean configured;
    private final RestClient client;

    @Autowired
    public CloudinaryLicenseImages(@Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
                                   @Value("${CLOUDINARY_API_KEY:${API_KEY:}}") String apiKey,
                                   @Value("${CLOUDINARY_API_SECRET:${API_SECRET:}}") String apiSecret) {
        this(cloudName, apiKey, apiSecret, restClientBuilder());
    }

    CloudinaryLicenseImages(String cloudName, String apiKey, String apiSecret, RestClient.Builder builder) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.configured = cloudName.matches("[a-z0-9_-]+") && !apiKey.isBlank() && !apiSecret.isBlank();
        this.client = builder.baseUrl("https://api.cloudinary.com/v1_1/" + (configured ? cloudName : "") + "/image").build();
    }



    private static RestClient.Builder restClientBuilder() {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requestFactory);
    }

    public static boolean isCloudReference(String value) {
        return value != null && REFERENCE.matcher(value).matches();
    }

    public String newReference(String localImage) {
        if (!configured) {
            throw new IllegalArgumentException("Chưa cấu hình Cloudinary cho ảnh GPLX. Vui lòng kiểm tra Cloud Name và API key.");
        }
        return REFERENCE_PREFIX + UUID.randomUUID() + (localImage.endsWith(".png") ? ".png" : ".jpg");
    }

    public void upload(String reference, byte[] image) {
        String publicId = publicId(reference);
        String format = format(reference);
        var form = sign(Map.of(
                "asset_folder", ASSET_FOLDER,
                "public_id", publicId,
                "type", DELIVERY_TYPE,
                "backup", "false",
                "overwrite", "false",
                "timestamp", Long.toString(Instant.now().getEpochSecond())));
        String mime = "png".equals(format) ? "image/png" : "image/jpeg";
        form.add("file", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image));
        try {
            Map<?, ?> result = post("/upload", form);
            if (result == null || !publicId.equals(result.get("public_id"))
                    || !DELIVERY_TYPE.equals(result.get("type")) || !format.equals(result.get("format"))) {
                logger.warn("Cloudinary trả về thông tin ảnh GPLX không đúng với yêu cầu tải lên");
                throw new IllegalArgumentException(UPLOAD_ERROR);
            }
        } catch (RestClientException e) {
            logFailure("tải ảnh lên", e);
            if (e instanceof RestClientResponseException response && response.getStatusCode().value() == 403) {
                throw new IllegalArgumentException("Cloudinary từ chối tải ảnh GPLX (HTTP 403). Vui lòng kiểm tra quyền tải ảnh của API key.");
            }
            throw new IllegalArgumentException(UPLOAD_ERROR);
        }
    }

    public byte[] read(String reference) {
        if (!configured) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình Cloudinary cho ảnh GPLX.");
        }
        long now = Instant.now().getEpochSecond();
        var query = sign(Map.of(
                "public_id", publicId(reference),
                "format", format(reference),
                "type", DELIVERY_TYPE,
                "expires_at", Long.toString(now + 60),
                "timestamp", Long.toString(now)));
        try {
            var response = client.get().uri(uri -> uri.path("/download").queryParams(query).build())
                    .retrieve().toEntity(byte[].class);
            byte[] image = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && image != null && image.length > 0) {
                return image;
            }
        } catch (RestClientException e) {
            if (e instanceof RestClientResponseException response && response.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh GPLX.");
            }
            logFailure("đọc ảnh", e);
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Không thể đọc ảnh GPLX từ Cloudinary.");
    }

    public void delete(String reference) {
        if (!configured) {
            logger.warn("Chưa cấu hình Cloudinary để dọn ảnh GPLX {}", reference);
            return;
        }
        var form = sign(Map.of(
                "public_id", publicId(reference),
                "type", DELIVERY_TYPE,
                "invalidate", "true",
                "timestamp", Long.toString(Instant.now().getEpochSecond())));
        try {
            Map<?, ?> result = post("/destroy", form);
            if (result == null || (!"ok".equals(result.get("result")) && !"not found".equals(result.get("result")))) {
                logger.warn("Cloudinary chưa xác nhận đã dọn ảnh GPLX {}", reference);
            }
        } catch (RestClientException e) {
            logFailure("dọn ảnh " + reference, e);
        }
    }

    private Map<?, ?> post(String path, MultiValueMap<String, String> form) {
        return client.post().uri(path).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
    }

    private MultiValueMap<String, String> sign(Map<String, String> parameters) {
        // Cloudinary signs the sorted parameter values before form or URL encoding.
        String payload = parameters.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("&"));
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest((payload + apiSecret).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var signed = new LinkedMultiValueMap<String, String>();
        parameters.forEach(signed::add);
        signed.add("api_key", apiKey);
        signed.add("signature", HexFormat.of().formatHex(digest));
        return signed;
    }

    private String publicId(String reference) {
        if (!isCloudReference(reference)) {
            throw new IllegalArgumentException("Mã ảnh Cloudinary không hợp lệ.");
        }
        return ASSET_FOLDER + "/" + reference.substring(REFERENCE_PREFIX.length(), reference.lastIndexOf('.'));
    }

    private String format(String reference) {
        return reference.substring(reference.lastIndexOf('.') + 1);
    }

    private void logFailure(String action, RestClientException exception) {
        // Response errors may include the signed download URL.
        String detail = exception instanceof RestClientResponseException response
                ? response.getStatusCode().toString() : exception.getClass().getSimpleName();
        logger.warn("Cloudinary không thể {} ({})", action, detail);
    }
}
