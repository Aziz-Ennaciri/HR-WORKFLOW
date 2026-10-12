package ma.rh.ai.hr_workflow.config;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.user.model.User;

/**
 * Issues and reads short-lived access tokens. The subject is the user id, so changing an email
 * does not invalidate the session; roles and the enabled flag are read from the database on
 * every request rather than trusted from the token.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    /** The user id of a valid, unexpired token; empty for anything else. */
    public Optional<Long> getUserId(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
