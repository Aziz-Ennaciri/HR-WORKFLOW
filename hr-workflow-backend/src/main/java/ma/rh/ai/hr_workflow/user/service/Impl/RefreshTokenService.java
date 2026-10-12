package ma.rh.ai.hr_workflow.user.service.Impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.user.model.RefreshToken;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.RefreshTokenRepository;

/**
 * Issues and rotates refresh tokens. Each token is single-use: refreshing revokes it and issues
 * a new one. Presenting an already-revoked token means it was stolen or replayed, so every
 * session of that user is revoked.
 */
@Service
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration lifetime;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${jwt.refresh-expiration-ms:604800000}") long lifetimeMs,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.lifetime = Duration.ofMillis(lifetimeMs);
        this.clock = clock;
    }

    /** Creates a refresh token for the user and returns its raw value (shown to the client once). */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(lifetime));
        refreshTokenRepository.save(token);
        return raw;
    }

    /**
     * Consumes a refresh token and returns its (enabled) user; the caller issues the new pair.
     * A rejected token does not roll the transaction back, so a detected replay stays revoked.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public User consume(String raw) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(raw))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        LocalDateTime now = LocalDateTime.now(clock);

        if (token.getRevokedAt() != null) {
            log.warn("Revoked refresh token reused for user {}; revoking all sessions", token.getUser().getId());
            refreshTokenRepository.revokeAllForUser(token.getUser().getId(), now);
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (!token.isUsable(now) || !token.getUser().isEnabled()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        token.setRevokedAt(now);
        return token.getUser();
    }

    /** Revokes one token (logout). Unknown or already revoked tokens are ignored. */
    @Transactional
    public void revoke(String raw) {
        refreshTokenRepository.findByTokenHash(hash(raw))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(LocalDateTime.now(clock)));
    }

    /** Revokes every session of a user (password change, account disabled). */
    @Transactional
    public void revokeAll(User user) {
        refreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now(clock));
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
