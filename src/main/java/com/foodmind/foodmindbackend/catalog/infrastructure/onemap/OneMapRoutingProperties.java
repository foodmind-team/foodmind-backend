package com.foodmind.foodmindbackend.catalog.infrastructure.onemap;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "foodmind.onemap.routing")
public class OneMapRoutingProperties {
    private boolean enabled;
    private String apiToken = "";
    private String credentialsSecretArn = "";
    private String credentialsRegion = "ap-southeast-1";
    private String baseUrl = "https://www.onemap.gov.sg";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(8);
    private Duration refreshSkew = Duration.ofMinutes(5);
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken == null ? "" : apiToken; }
    public String getCredentialsSecretArn() { return credentialsSecretArn; }
    public void setCredentialsSecretArn(String credentialsSecretArn) { this.credentialsSecretArn = credentialsSecretArn == null ? "" : credentialsSecretArn; }
    public String getCredentialsRegion() { return credentialsRegion; }
    public void setCredentialsRegion(String credentialsRegion) { this.credentialsRegion = credentialsRegion; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getRefreshSkew() { return refreshSkew; }
    public void setRefreshSkew(Duration refreshSkew) { this.refreshSkew = refreshSkew; }
}
