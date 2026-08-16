package com.foodmind.foodmindbackend.catalog.infrastructure.onemap;

import com.foodmind.foodmindbackend.catalog.domain.GeoPoint;
import com.foodmind.foodmindbackend.catalog.domain.WalkingRoute;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OneMapRoutingClient {
    private final ObjectMapper objectMapper;
    private final OneMapTokenProvider tokenProvider;
    private final RestClient routingClient;

    public OneMapRoutingClient(
            OneMapRoutingProperties properties,
            ObjectMapper objectMapper,
            OneMapTokenProvider tokenProvider) {
        this.objectMapper = objectMapper;
        this.tokenProvider = tokenProvider;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.routingClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    public WalkingRoute walkingRoute(GeoPoint origin, GeoPoint destination) {
        String token = tokenProvider.token();
        try {
            return requestRoute(origin, destination, token);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 && tokenProvider.invalidate(token)) {
                try {
                    return requestRoute(origin, destination, tokenProvider.token());
                } catch (Exception retryFailure) {
                    throw unavailable();
                }
            }
            throw unavailable();
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private WalkingRoute requestRoute(GeoPoint origin, GeoPoint destination, String token) throws Exception {
        String raw = routingClient.get()
                .uri(builder -> builder.path("/api/public/routingsvc/route")
                        .queryParam("start", origin.latitude() + "," + origin.longitude())
                        .queryParam("end", destination.latitude() + "," + destination.longitude())
                        .queryParam("routeType", "walk")
                        .build())
                .header("Authorization", token)
                .retrieve()
                .body(String.class);
        JsonNode root = objectMapper.readTree(raw);
        if (root.path("status").asInt(-1) != 0) {
            throw unavailable();
        }
        JsonNode summary = root.path("route_summary");
        List<GeoPoint> coordinates = decodePolyline(root.path("route_geometry").asText(""));
        if (coordinates.size() < 2) {
            throw unavailable();
        }
        return new WalkingRoute(origin, destination, summary.path("total_distance").asLong(),
                summary.path("total_time").asLong(), coordinates);
    }

    private static List<GeoPoint> decodePolyline(String encoded) {
        List<GeoPoint> points = new ArrayList<>();
        int index = 0;
        int latitude = 0;
        int longitude = 0;
        while (index < encoded.length()) {
            int[] lat = decodeValue(encoded, index);
            index = lat[1];
            latitude += lat[0];
            int[] lng = decodeValue(encoded, index);
            index = lng[1];
            longitude += lng[0];
            points.add(new GeoPoint(BigDecimal.valueOf(latitude / 1e5),
                    BigDecimal.valueOf(longitude / 1e5)));
        }
        return points;
    }

    private static int[] decodeValue(String encoded, int index) {
        int result = 0;
        int shift = 0;
        int value;
        do {
            if (index >= encoded.length()) {
                throw unavailable();
            }
            value = encoded.charAt(index++) - 63;
            result |= (value & 0x1f) << shift;
            shift += 5;
        } while (value >= 0x20);
        return new int[] {(result & 1) != 0 ? ~(result >> 1) : result >> 1, index};
    }

    private static ApiException unavailable() {
        return new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE,
                "Walking directions are temporarily unavailable.");
    }
}
