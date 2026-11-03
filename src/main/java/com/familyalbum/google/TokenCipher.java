package com.familyalbum.google;

import com.familyalbum.config.AppProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** refresh token 저장용 AES-256-GCM 암복호화. 키: app.google.token-key (base64, 32바이트). */
@Component
public class TokenCipher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKey key; // 구글 연동이 꺼져 있으면 null

    public TokenCipher(AppProperties props) {
        if (props.google().enabled()) {
            String k = props.google().tokenKey();
            if (k == null || k.isBlank()) {
                throw new IllegalStateException(
                        "구글 연동이 켜져 있으면 GOOGLE_TOKEN_KEY(base64 32바이트)가 필요합니다. 예: openssl rand -base64 32");
            }
            byte[] raw = Base64.getDecoder().decode(k.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("GOOGLE_TOKEN_KEY 는 base64 로 인코딩된 32바이트여야 합니다.");
            }
            this.key = new SecretKeySpec(raw, "AES");
        } else {
            this.key = null;
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("토큰 암호화 실패", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("토큰 복호화 실패(키가 바뀌었을 수 있어요). 구글 계정을 다시 연결해 주세요.", e);
        }
    }

    private SecretKey requireKey() {
        if (key == null) throw new IllegalStateException("구글 연동이 비활성화되어 있습니다.");
        return key;
    }
}
