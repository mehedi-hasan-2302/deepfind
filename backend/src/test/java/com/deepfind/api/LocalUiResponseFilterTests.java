package com.deepfind.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LocalUiResponseFilterTests {
    @Test
    void constrainsThePackagedUiWithoutInlineScriptsOrRemoteConnections() throws Exception {
        var request = new MockHttpServletRequest("GET", "/");
        var response = new MockHttpServletResponse();
        new LocalUiResponseFilter().doFilter(request, response, (req, res) -> {});
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("script-src 'self'", "connect-src 'self'", "frame-ancestors 'none'")
                .doesNotContain("unsafe-inline", "unsafe-eval", "https:");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void leavesApiHeadersToTheApiFilter() throws Exception {
        var response = new MockHttpServletResponse();
        new LocalUiResponseFilter()
                .doFilter(new MockHttpServletRequest("GET", "/api/health"), response, (req, res) -> {});
        assertThat(response.getHeader("Content-Security-Policy")).isNull();
    }
}
