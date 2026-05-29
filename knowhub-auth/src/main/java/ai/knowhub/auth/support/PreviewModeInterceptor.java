package ai.knowhub.auth.support;

import ai.knowhub.auth.config.PreviewModeProperties;
import ai.knowhub.common.ApiResponse;
import ai.knowhub.web.ApiVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Blocks write APIs when KnowHub AI runs in read-only preview mode.
 */
@Component
public class PreviewModeInterceptor implements HandlerInterceptor {

    private static final String CHAT_STREAM_PATH = ApiVersion.V1_CHAT + "/stream";

    private static final Set<String> BLOCKED_PATHS = Set.of(
        CHAT_STREAM_PATH,
        ApiVersion.V1_CHAT + "/session/stop",
        ApiVersion.V1_CHAT + "/session/reset",
        ApiVersion.V1_CHAT + "/session/summary/rebuild",
        ApiVersion.V1_MANAGE_DOCUMENT + "/upload",
        ApiVersion.V1_MANAGE_DOCUMENT + "/delete",
        ApiVersion.V1_MANAGE_DOCUMENT + "/strategy/confirm",
        ApiVersion.V1_MANAGE_DOCUMENT + "/index/build",
        ApiVersion.V1_MANAGE_KNOWLEDGE + "/scope/save",
        ApiVersion.V1_MANAGE_KNOWLEDGE + "/scope/delete",
        ApiVersion.V1_MANAGE_KNOWLEDGE + "/topic/save",
        ApiVersion.V1_MANAGE_KNOWLEDGE + "/topic/delete",
        ApiVersion.V1_MANAGE_KNOWLEDGE + "/document/profile/regenerate",
        ApiVersion.V1_MANAGE_KNOWLEDGE + "/document/profile/batch/regenerate",
        ApiVersion.V1_MANAGE_KNOWLEDGE + "/topic/document/save",
        ApiVersion.V1_MANAGE_KNOWLEDGE + "/topic/document/remove"
    );

    private final PreviewModeProperties previewModeProperties;

    private final ObjectMapper objectMapper;

    public PreviewModeInterceptor(PreviewModeProperties previewModeProperties,
                                  ObjectMapper objectMapper) {
        this.previewModeProperties = previewModeProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!Boolean.TRUE.equals(previewModeProperties.getEnabled())) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!BLOCKED_PATHS.contains(path)) {
            return true;
        }

        if (CHAT_STREAM_PATH.equals(path)) {
            writeStreamReject(response);
        }
        else {
            writeJsonReject(response);
        }
        return false;
    }

    private void writeStreamReject(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("content", previewModeProperties.getMessage());
        payload.put("timestamp", Instant.now().toString());

        response.getWriter().write("data: " + objectMapper.writeValueAsString(payload) + "\n\n");
        response.getWriter().flush();
    }

    private void writeJsonReject(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(-403, previewModeProperties.getMessage())));
        response.getWriter().flush();
    }
}
