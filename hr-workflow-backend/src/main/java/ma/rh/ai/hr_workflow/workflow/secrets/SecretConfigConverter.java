package ma.rh.ai.hr_workflow.workflow.secrets;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Encrypts secret fields of node configs at rest with AES-256-GCM. Only the secret values are
 * encrypted ({@code "apiKey": "enc:v1:..."}); the rest of the config stays readable. Values
 * written before encryption existed are read as plaintext and encrypted on the next save.
 */
@Component
@Converter
public class SecretConfigConverter implements AttributeConverter<String, String> {

    static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public SecretConfigConverter(@Value("${security.config-encryption-key}") String base64Key) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("security.config-encryption-key must be base64 (openssl rand -base64 32)");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("security.config-encryption-key must decode to 32 bytes (openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String configJson) {
        return transform(configJson, true);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return transform(stored, false);
    }

    private String transform(String json, boolean encrypt) {
        ObjectNode config = ConfigSecrets.parseObject(json);
        if (config == null) {
            return json;
        }
        boolean changed = false;
        for (String field : ConfigSecrets.SECRET_FIELDS) {
            JsonNode value = config.get(field);
            if (value == null || !value.isTextual() || value.asText().isEmpty()) {
                continue;
            }
            String text = value.asText();
            boolean encrypted = text.startsWith(PREFIX);
            if (encrypt && !encrypted) {
                config.put(field, encrypt(text));
                changed = true;
            } else if (!encrypt && encrypted) {
                config.put(field, decrypt(text));
                changed = true;
            }
        }
        return changed ? config.toString() : json;
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a config secret", e);
        }
    }

    private String decrypt(String stored) {
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a config secret (wrong CONFIG_ENCRYPTION_KEY?)", e);
        }
    }
}
