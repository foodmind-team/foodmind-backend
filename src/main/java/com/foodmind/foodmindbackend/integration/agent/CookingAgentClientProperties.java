package com.foodmind.foodmindbackend.integration.agent;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 12:10 pm
 */

@ConfigurationProperties(prefix = "foodmind.cooking.agent")
public class CookingAgentClientProperties {

    private boolean enabled;
    private String baseUrl = "http://127.0.0.1:65535";
    private String endpointPath = "/internal/v1/agents/cooking-plan/generate";
    private String tasksBasePath = "/internal/v2/cooking-plan/tasks";
    private String serviceToken = "";
    private Duration connectTimeout = Duration.ofMillis(250);
    private Duration readTimeout = Duration.ofSeconds(30);
    private int maxResponseBytes = 1_048_576;
    private String region = "SG";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEndpointPath() {
        return endpointPath;
    }

    public void setEndpointPath(String endpointPath) {
        this.endpointPath = endpointPath;
    }

    public String getTasksBasePath() {
        return tasksBasePath;
    }

    public void setTasksBasePath(String tasksBasePath) {
        this.tasksBasePath = tasksBasePath;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
