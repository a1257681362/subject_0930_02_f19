package com.evops.aqua.fulfill.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 海水养殖管理 HTTP 网关：POST JSON 注册拆单事件（at-least-once，对端按 eventId 幂等）。
 * 连接/读取超时、非 2xx 响应一律转 ExternalUnavailableException——调用方绝不把失败当成功。
 * 启用方式：aqua.fulfill.gateway.mode=http。
 */
@Component
@ConditionalOnProperty(name = "aqua.fulfill.gateway.mode", havingValue = "http")
public class HttpMarineManageGateway implements MarineManageGateway {

    private final ObjectMapper objectMapper;

    @Value("${aqua.fulfill.gateway.http.url:http://marine-manage.local/api/split-registrations}")
    private String url;

    @Value("${aqua.fulfill.gateway.http.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${aqua.fulfill.gateway.http.read-timeout-ms:5000}")
    private int readTimeoutMs;

    public HttpMarineManageGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String mode() {
        return "http";
    }

    @Override
    public void registerSplit(String eventId, Map<String, Object> payload) {
        HttpURLConnection connection = null;
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("X-Event-Id", eventId);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new ExternalUnavailableException("海水养殖管理返回非成功状态码 " + code + "，eventId=" + eventId);
            }
        } catch (ExternalUnavailableException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ExternalUnavailableException("海水养殖管理无响应：" + ex.getMessage() + "，eventId=" + eventId, ex);
        } catch (Exception ex) {
            throw new ExternalUnavailableException("海水养殖管理调用失败：" + ex.getMessage() + "，eventId=" + eventId, ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
