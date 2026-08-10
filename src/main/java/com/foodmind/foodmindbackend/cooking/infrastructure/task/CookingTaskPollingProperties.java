package com.foodmind.foodmindbackend.cooking.infrastructure.task;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Background polling of async cooking-agent tasks ({@code foodmind.cooking.task.*}).
 * Polling defaults on because the Web client submits asynchronous tasks and
 * relies on Backend materialisation to observe terminal Agent results.
 */
@ConfigurationProperties(prefix = "foodmind.cooking.task")
public class CookingTaskPollingProperties {

    private boolean pollEnabled = true;
    private Duration pollInterval = Duration.ofSeconds(2);
    private int pollBatch = 20;
    private int maxAttempts = 5;
    private Duration maxBackoff = Duration.ofSeconds(60);

    public boolean isPollEnabled() {
        return pollEnabled;
    }

    public void setPollEnabled(boolean pollEnabled) {
        this.pollEnabled = pollEnabled;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public int getPollBatch() {
        return pollBatch;
    }

    public void setPollBatch(int pollBatch) {
        this.pollBatch = pollBatch;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
    }
}
