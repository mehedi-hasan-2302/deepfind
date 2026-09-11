package com.deepfind.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Security headers for the production UI served by the loopback backend, not Tauri's asset protocol. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class LocalUiResponseFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals("/api") || request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(
                "Content-Security-Policy",
                "default-src 'none'; script-src 'self'; style-src 'self'; "
                        + "img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'none'; "
                        + "form-action 'none'; frame-ancestors 'none'; object-src 'none'");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
