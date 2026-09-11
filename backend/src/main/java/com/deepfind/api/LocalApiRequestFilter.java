package com.deepfind.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class LocalApiRequestFilter extends OncePerRequestFilter {

    public static final String CLIENT_HEADER = "X-DeepFind-Client";
    public static final String CLIENT_HEADER_VALUE = "browser";

    private static final Set<String> LOCAL_HOSTS = Set.of("127.0.0.1", "localhost", "::1");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String REJECTION_BODY =
            "{\"code\":\"LOCAL_API_REQUEST_REJECTED\",\"message\":\"DeepFind rejected a non-local API request.\",\"details\":{}}";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return !requestUri.equals("/api") && !requestUri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        addResponseHeaders(response);
        if (!hasLocalAuthority(request) || !hasLocalBrowserSource(request) || !hasRequiredMutationHeader(request)) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean hasLocalAuthority(HttpServletRequest request) {
        String hostHeader = request.getHeader("Host");
        if (hostHeader == null || hostHeader.isBlank()) {
            return isLocalHost(request.getServerName());
        }
        return isLocalHost(parseAuthorityHost(hostHeader));
    }

    private static boolean hasLocalBrowserSource(HttpServletRequest request) {
        if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) {
            return false;
        }
        String origin = request.getHeader("Origin");
        if (origin != null) {
            return isLocalHttpUri(origin, true);
        }
        String referer = request.getHeader("Referer");
        return referer == null || isLocalHttpUri(referer, false);
    }

    private static boolean hasRequiredMutationHeader(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))
                || CLIENT_HEADER_VALUE.equals(request.getHeader(CLIENT_HEADER));
    }

    private static String parseAuthorityHost(String authority) {
        try {
            return new URI("http://" + authority).getHost();
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static boolean isLocalHttpUri(String value, boolean requireOriginShape) {
        try {
            URI uri = new URI(value);
            boolean localScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            boolean validShape = !requireOriginShape
                    || ((uri.getRawPath() == null || uri.getRawPath().isEmpty())
                            && uri.getRawQuery() == null
                            && uri.getRawFragment() == null);
            return localScheme && validShape && uri.getUserInfo() == null && isLocalHost(uri.getHost());
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static boolean isLocalHost(String host) {
        return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    private static void addResponseHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(REJECTION_BODY);
    }
}
