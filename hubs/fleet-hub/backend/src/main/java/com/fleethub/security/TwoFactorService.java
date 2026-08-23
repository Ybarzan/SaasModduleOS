package com.fleethub.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

@Service
@Slf4j
public class TwoFactorService {

    private static final int SECRET_BYTES = 20;
    private static final int CODE_DIGITS = 6;
    private static final long TIME_STEP_MS = 30_000;
    private static final int VERIFY_WINDOW = 1;
    private static final String HMAC_ALGO = "HmacSHA1";
    private static final char[] BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final byte[] BASE32_LOOKUP = new byte[128];

    static {
        java.util.Arrays.fill(BASE32_LOOKUP, (byte) -1);
        for (int i = 0; i < BASE32_CHARS.length; i++) {
            BASE32_LOOKUP[BASE32_CHARS[i]] = (byte) i;
        }
    }

    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String buildOtpAuthUri(String username, String secret) {
        String issuer = "Fleet Hub";
        return "otpauth://totp/" + issuer + ":" + username
                + "?secret=" + secret
                + "&issuer=" + issuer
                + "&digits=" + CODE_DIGITS
                + "&period=" + (TIME_STEP_MS / 1000);
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) return false;
        long currentStep = System.currentTimeMillis() / TIME_STEP_MS;
        for (long offset = -VERIFY_WINDOW; offset <= VERIFY_WINDOW; offset++) {
            String expected = computeCode(secret, currentStep + offset);
            if (expected.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String computeCode(String secret, long step) {
        try {
            byte[] secretBytes = base32Decode(secret);
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGO));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int code = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            code = code % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", code);
        } catch (Exception e) {
            log.error("Erreur calcul TOTP", e);
            return "";
        }
    }

    private static String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        int index = 0;
        while (i < data.length) {
            int currByte = data[i] & 0xFF;
            int digit;
            if (index > 3) {
                int nextByte = (i + 1) < data.length ? data[i + 1] & 0xFF : 0;
                digit = currByte & (0xFF >> index);
                index = (index + 5) % 8;
                digit <<= index;
                digit |= nextByte >> (8 - index);
                i++;
            } else {
                digit = (currByte >> (8 - (index + 5))) & 0x1F;
                index = (index + 5) % 8;
                if (index == 0) i++;
            }
            result.append(BASE32_CHARS[digit]);
        }
        return result.toString();
    }

    private static byte[] base32Decode(String encoded) {
        String str = encoded.toUpperCase().replace("=", "");
        int numBytes = str.length() * 5 / 8;
        byte[] result = new byte[numBytes];
        int buffer = 0;
        int bitsLeft = 0;
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c >= BASE32_LOOKUP.length) continue;
            int val = BASE32_LOOKUP[c];
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[count++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
