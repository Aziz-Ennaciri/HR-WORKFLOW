package ma.rh.ai.hr_workflow.config;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helpers to read the authenticated user (populated from the JWT by {@link JwtAuthenticationFilter}). */
public final class SecurityUtils {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private SecurityUtils() {
    }

    /** Email of the authenticated user (the JWT subject). */
    public static String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return auth.getName();
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
    }
}
