package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilterRequest extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // ✅ Always allow CORS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ Public endpoints (no JWT required)
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            // Not authenticated -> let Spring Security decide (it will 401 for protected routes)
            filterChain.doFilter(request, response);
            return;
        }

        String jwtToken = authorizationHeader.substring(7);

        try {
            String username = jwtUtils.extractUserName(jwtToken);
            UserDetails currentUserDetails = customUserDetailsService.loadUserByUsername(username);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Boolean tokenValidated = jwtUtils.validateToken(jwtToken, currentUserDetails);
                if (tokenValidated) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    currentUserDetails, null, currentUserDetails.getAuthorities()
                            );
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("Invalid JWT token");
                    return;
                }
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or expired JWT token");
        }
    }

    private boolean isPublicPath(String path) {
        // Stripe
        if (path.startsWith("/stripe/v1/")) return true;

        // School endpoints used on register page
        if (path.startsWith("/school/v1/search")) return true;
        if (path.startsWith("/school/v1/newSchool")) return true;
        if (path.startsWith("/school/v1/all")) return true;

        // Auth / login / oauth routes
        if (path.startsWith("/login")) return true;
        if (path.startsWith("/oauth2/")) return true;
        if (path.startsWith("/error")) return true;

        // Explicit public auth routes. /auth/me must still pass through this
        // filter so the bearer token can establish the security context.
        if (path.equals("/auth")) return true;
        if (path.equals("/auth/refresh")) return true;

        // Any other public pages listed in SecurityConfig
        if (path.startsWith("/register")) return true;
        if (path.startsWith("/contact-us")) return true;
        if (path.startsWith("/forgot-password")) return true;
        if (path.startsWith("/reset-password")) return true;

        return false;
    }
}
