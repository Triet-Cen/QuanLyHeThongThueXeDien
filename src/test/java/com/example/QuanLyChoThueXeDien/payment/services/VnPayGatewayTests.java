package com.example.QuanLyChoThueXeDien.payment.services;

import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VnPayGatewayTests {
    private final VnPayGateway gateway = new VnPayGateway("TEST","fixture-only","https://gateway.invalid/pay",
        "https://merchant.invalid/payments/vnpay/return","https://merchant.invalid/payments/vnpay/ipn",30);
    private final LocalDateTime now = LocalDateTime.of(2030,1,1,8,10);

    static String checksum(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("fixture-only".getBytes(StandardCharsets.UTF_8),"HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void vp04_25_signedUrlUsesVndTimes100AndOriginalDeadline() throws Exception {
        String url = gateway.buildPaymentUrl("REF",new BigDecimal("99999999"),now,now.plusMinutes(5),"127.0.0.1");
        String canonical = "vnp_Amount=9999999900&vnp_Command=pay&vnp_CreateDate=20300101081000"
            +"&vnp_CurrCode=VND&vnp_ExpireDate=20300101081500&vnp_IpAddr=127.0.0.1&vnp_Locale=vn"
            +"&vnp_OrderInfo=Thanh+toan+REF&vnp_OrderType=other"
            +"&vnp_ReturnUrl=https%3A%2F%2Fmerchant.invalid%2Fpayments%2Fvnpay%2Freturn"
            +"&vnp_TmnCode=TEST&vnp_TxnRef=REF&vnp_Version=2.1.0";
        assertEquals(canonical+"&vnp_SecureHash="+checksum(canonical),URI.create(url).getRawQuery());
    }

    @Test void vp07_25_verifiesIndependentSignedFixtureAndRejectsTamperOrDuplicateFields() throws Exception {
        String data = "vnp_Amount=1234500&vnp_OrderInfo=Thu+phi&vnp_ResponseCode=00&vnp_TmnCode=TEST"
            +"&vnp_TransactionNo=123&vnp_TransactionStatus=00&vnp_TxnRef=REF";
        Map<String,String[]> payload = new HashMap<>();
        for (String field : data.split("&")) {
            var parts = field.split("=",2);
            payload.put(parts[0],new String[]{URLDecoder.decode(parts[1],StandardCharsets.UTF_8)});
        }
        payload.put("vnp_SecureHash",new String[]{checksum(data)});
        var event = gateway.verifyIpn(payload).orElseThrow();
        assertTrue(event.successful());
        assertEquals(new BigDecimal("12345.00"),event.amount());
        payload.put("vnp_Amount",new String[]{"1234500","1234500"});
        assertTrue(gateway.verifyIpn(payload).isEmpty());
        payload.put("vnp_Amount",new String[]{"1234501"});
        assertTrue(gateway.verifyIpn(payload).isEmpty());
        payload.remove("vnp_SecureHash");
        assertTrue(gateway.verifyIpn(payload).isEmpty());
    }

    @Test void vp26_missingCallbackAndFeePolicyFailClosed() {
        var missing = new VnPayGateway("TEST","fixture-only","https://gateway.invalid/pay","","",0);
        assertThrows(IllegalArgumentException.class,()->missing.buildPaymentUrl("REF",BigDecimal.ONE,now,now.plusMinutes(1),"::1"));
        assertThrows(IllegalArgumentException.class,()->missing.getFeePaymentDeadline(now));
        assertEquals(now.plusMinutes(30),gateway.getFeePaymentDeadline(now));
    }
}
