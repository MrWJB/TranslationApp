package com.translationapp.translator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Minimal Tencent Cloud API 3.0 (TC3-HMAC-SHA256) signer for TMT TextTranslate.
 */
final class TencentCloudSigner {

    private static final DateTimeFormatter ISO8601 =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private TencentCloudSigner() {
    }

    static SignedRequest signTextTranslate(
            String secretId,
            String secretKey,
            String region,
            String host,
            String payload) {
        try {
            String service = "tmt";
            String action = "TextTranslate";
            String version = "2018-03-21";
            long timestamp = Instant.now().getEpochSecond();
            String date = ISO8601.format(Instant.ofEpochSecond(timestamp));

            String canonicalRequest = "POST\n/\n\ncontent-type:application/json; charset=utf-8\nhost:" + host + "\n\n"
                    + "content-type;host\n"
                    + sha256Hex(payload);

            String credentialScope = date + "/" + service + "/tc3_request";
            String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n"
                    + sha256Hex(canonicalRequest);

            byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
            byte[] secretService = hmacSha256(secretDate, service);
            byte[] secretSigning = hmacSha256(secretService, "tc3_request");
            String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));

            String authorization = "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                    + ", SignedHeaders=content-type;host, Signature=" + signature;

            return new SignedRequest(authorization, timestamp, action, version, region);
        } catch (Exception e) {
            throw new IllegalStateException("Tencent Cloud signing failed", e);
        }
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static byte[] hmacSha256(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    record SignedRequest(String authorization, long timestamp, String action, String version, String region) {
    }
}
