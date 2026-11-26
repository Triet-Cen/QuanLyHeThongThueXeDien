package com.example.QuanLyChoThueXeDien.payment.services;

import com.example.QuanLyChoThueXeDien.payment.dtos.VnPayEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class VnPayGateway {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final String merchant, secret, gateway, returnUrl, ipnUrl;
    private final int feeMinutes;

    VnPayGateway(@Value("${VNPAY_TMN_CODE:}") String merchant, @Value("${VNPAY_HASH_SECRET:}") String secret,
                 @Value("${VNPAY_URL:}") String gateway, @Value("${VNPAY_RETURN_URL:}") String returnUrl,
                 @Value("${VNPAY_IPN_URL:}") String ipnUrl, @Value("${VNPAY_FEE_ATTEMPT_MINUTES:15}") int feeMinutes) {
        this.merchant = merchant;
        this.secret = secret;
        this.gateway = gateway;
        this.returnUrl = returnUrl;
        this.ipnUrl = ipnUrl;
        this.feeMinutes = feeMinutes;
    }

    String merchant() { return merchant; }

    LocalDateTime getFeePaymentDeadline(LocalDateTime now) {
        if (feeMinutes <= 0) throw new IllegalArgumentException("Chưa cấu hình thời hạn lần thanh toán phí.");
        return now.plusMinutes(feeMinutes);
    }

    String buildPaymentUrl(String ref, BigDecimal amount, LocalDateTime created, LocalDateTime expires, String ip) {
        if (secret.isBlank() || merchant.isBlank() || !isHttpsUrl(gateway) || !isHttpsUrl(returnUrl) || !isHttpsUrl(ipnUrl))
            throw new IllegalArgumentException("Chưa đủ cấu hình merchant, URL thanh toán, Return hoặc IPN HTTPS.");
        var p = new TreeMap<String,String>();
        p.put("vnp_Version","2.1.0");
        p.put("vnp_Command","pay");
        p.put("vnp_TmnCode",merchant);
        p.put("vnp_Amount",amount.movePointRight(2).toBigIntegerExact().toString());
        p.put("vnp_CurrCode","VND");
        p.put("vnp_TxnRef",ref);
        p.put("vnp_OrderInfo","Thanh toan " + ref);
        p.put("vnp_OrderType","other");
        p.put("vnp_Locale","vn");
        p.put("vnp_ReturnUrl",returnUrl);
        p.put("vnp_IpAddr",ip);
        p.put("vnp_CreateDate",DATE.format(created));
        p.put("vnp_ExpireDate",DATE.format(expires));
        String query = buildSignedQuery(p);
        return gateway + "?" + query + "&vnp_SecureHash=" + createSignature(query);
    }

    private static boolean isHttpsUrl(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException invalid) { return false; }
    }

    Optional<VnPayEvent> verifyIpn(Map<String,String[]> input) {
        if (secret.isBlank() || merchant.isBlank() || input.size() > 40) return Optional.empty();
        var fields = new TreeMap<String,String>();
        for (var entry : input.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            if (!key.matches("vnp_[A-Za-z0-9]{1,40}") || values.length != 1 || values[0].length() > 512)
                return Optional.empty();
            if (!values[0].isEmpty()) fields.put(key, values[0]);
        }
        String hash = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");
        if (hash == null || !hash.matches("[a-fA-F0-9]{128}") ||
            !MessageDigest.isEqual(HexFormat.of().parseHex(hash), HexFormat.of().parseHex(createSignature(buildSignedQuery(fields)))))
            return Optional.empty();
        String ref = fields.getOrDefault("vnp_TxnRef","");
        String code = fields.getOrDefault("vnp_TmnCode","");
        String amount = fields.getOrDefault("vnp_Amount","");
        String response = fields.getOrDefault("vnp_ResponseCode","");
        String status = fields.getOrDefault("vnp_TransactionStatus","");
        String currency = fields.getOrDefault("vnp_CurrCode","VND"); // PAY IPN normally omits currency.
        String id = fields.getOrDefault("vnp_TransactionNo","");
        String date = fields.get("vnp_PayDate");
        if (!ref.matches("[A-Za-z0-9]{1,100}") || !code.matches("[A-Za-z0-9]{1,32}") ||
            !amount.matches("[0-9]{1,18}") || !response.matches("[0-9]{2}") || !status.matches("[0-9]{2}") ||
            !currency.matches("[A-Z]{3}") || !id.matches("[0-9]{0,15}") || date != null && !date.matches("[0-9]{14}"))
            return Optional.empty();
        if ("00".equals(response) && "00".equals(status) && (id.isEmpty() || id.matches("0+"))) return Optional.empty();
        return Optional.of(new VnPayEvent(ref,code,id,new BigDecimal(amount).movePointLeft(2),currency,date,response,status,
            hash.toLowerCase(Locale.ROOT)));
    }

    private static String buildSignedQuery(SortedMap<String,String> fields) {
        var query = new StringJoiner("&");
        fields.forEach((k,v) -> query.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return query.toString();
    }

    private String createSignature(String data) {
        try {
            var mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}
