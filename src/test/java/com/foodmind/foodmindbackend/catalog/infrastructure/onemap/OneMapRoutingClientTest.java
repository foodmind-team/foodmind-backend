package com.foodmind.foodmindbackend.catalog.infrastructure.onemap;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodmind.foodmindbackend.catalog.domain.GeoPoint;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OneMapRoutingClientTest {
    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
    private WireMockServer server;
    private MutableClock clock;
    private OneMapRoutingProperties properties;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(0);
        server.start();
        clock = new MutableClock(NOW);
        properties = new OneMapRoutingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(server.baseUrl());
        properties.setCredentialsSecretArn(
                "arn:aws:secretsmanager:ap-southeast-1:123456789012:secret:foodmind/staging/onemap");
        properties.setRefreshSkew(Duration.ofMinutes(5));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void cachesOneDynamicTokenAcrossConcurrentCallers() {
        stubToken("cached-token", NOW.plus(Duration.ofDays(3)).getEpochSecond());
        OneMapTokenProvider provider = provider();

        List<String> values = IntStream.range(0, 12)
                .mapToObj(index -> CompletableFuture.supplyAsync(provider::token))
                .toList().stream()
                .map(CompletableFuture::join)
                .toList();

        assertThat(values).containsOnly("cached-token");
        server.verify(1, postRequestedFor(urlPathEqualTo("/api/auth/post/getToken")));
    }

    @Test
    void refreshesBeforeExpiryAndReadsCredentialsAgain() {
        server.stubFor(post(urlPathEqualTo("/api/auth/post/getToken"))
                .inScenario("refresh")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(tokenResponse("first-token", NOW.plusSeconds(301).getEpochSecond()))
                .willSetStateTo("second"));
        server.stubFor(post(urlPathEqualTo("/api/auth/post/getToken"))
                .inScenario("refresh")
                .whenScenarioStateIs("second")
                .willReturn(tokenResponse("second-token", NOW.plus(Duration.ofDays(3)).getEpochSecond())));
        CountingCredentialsSource credentials = new CountingCredentialsSource();
        OneMapTokenProvider provider = provider(credentials);

        assertThat(provider.token()).isEqualTo("first-token");
        clock.advance(Duration.ofSeconds(2));
        assertThat(provider.token()).isEqualTo("second-token");
        assertThat(credentials.loads).isEqualTo(2);
    }

    @Test
    void retriesRoutingOnceWithARefreshedTokenAfterUnauthorized() {
        server.stubFor(post(urlPathEqualTo("/api/auth/post/getToken"))
                .inScenario("unauthorized")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(tokenResponse("expired-token", NOW.plus(Duration.ofDays(3)).getEpochSecond()))
                .willSetStateTo("refreshed"));
        server.stubFor(post(urlPathEqualTo("/api/auth/post/getToken"))
                .inScenario("unauthorized")
                .whenScenarioStateIs("refreshed")
                .willReturn(tokenResponse("fresh-token", NOW.plus(Duration.ofDays(3)).getEpochSecond())));
        server.stubFor(get(urlPathEqualTo("/api/public/routingsvc/route"))
                .withHeader("Authorization", equalTo("expired-token"))
                .willReturn(aResponse().withStatus(401).withBody("do not expose this response")));
        server.stubFor(get(urlPathEqualTo("/api/public/routingsvc/route"))
                .withHeader("Authorization", equalTo("fresh-token"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"status":0,"route_geometry":"{u`GktxxR?G",
                                 "route_summary":{"total_distance":5,"total_time":3}}
                                """)));

        OneMapTokenProvider provider = provider();
        OneMapRoutingClient client = new OneMapRoutingClient(properties, JsonMapper.builder().build(), provider);
        var route = client.walkingRoute(point("1.319728", "103.8421"),
                point("1.319729", "103.8421581"));

        assertThat(route.distanceMeters()).isEqualTo(5);
        assertThat(route.coordinates()).hasSizeGreaterThanOrEqualTo(2);
        server.verify(2, postRequestedFor(urlPathEqualTo("/api/auth/post/getToken")));
        server.verify(2, getRequestedFor(urlPathEqualTo("/api/public/routingsvc/route")));
    }

    @Test
    void preservesTheStaticTokenCompatibilityFallback() {
        properties.setCredentialsSecretArn("");
        properties.setApiToken("local-static-token");
        OneMapTokenProvider provider = provider(() -> {
            throw new AssertionError("Static token mode must not load AWS credentials.");
        });

        assertThat(provider.token()).isEqualTo("local-static-token");
        assertThat(provider.invalidate("local-static-token")).isFalse();
    }

    @Test
    void mapsAuthenticationFailuresToAStableMessageWithoutCredentialOrUpstreamContent() {
        server.stubFor(post(urlPathEqualTo("/api/auth/post/getToken"))
                .willReturn(aResponse().withStatus(401)
                        .withBody("owner@example.test safe-password upstream-secret")));

        assertThatThrownBy(() -> provider().token())
                .isInstanceOf(ApiException.class)
                .hasMessage("Walking directions are temporarily unavailable.")
                .hasMessageNotContaining("owner@example.test")
                .hasMessageNotContaining("safe-password")
                .hasMessageNotContaining("upstream-secret");
    }

    private OneMapTokenProvider provider() {
        return provider(new CountingCredentialsSource());
    }

    private OneMapTokenProvider provider(OneMapCredentialsSource credentialsSource) {
        return new OneMapTokenProvider(properties, credentialsSource,
                JsonMapper.builder().build(), clock);
    }

    private void stubToken(String token, long expiry) {
        server.stubFor(post(urlPathEqualTo("/api/auth/post/getToken"))
                .withRequestBody(equalToJson("{\"password\":\"safe-password\",\"email\":\"owner@example.test\"}"))
                .willReturn(tokenResponse(token, expiry)));
    }

    private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder tokenResponse(String token, long expiry) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"" + token + "\",\"expiry_timestamp\":\"" + expiry + "\"}");
    }

    private GeoPoint point(String latitude, String longitude) {
        return new GeoPoint(new BigDecimal(latitude), new BigDecimal(longitude));
    }

    private static final class CountingCredentialsSource implements OneMapCredentialsSource {
        private int loads;

        @Override
        public synchronized OneMapCredentials load() {
            loads++;
            return new OneMapCredentials("owner@example.test", "safe-password");
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
