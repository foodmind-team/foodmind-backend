package com.foodmind.foodmindbackend.inventory.api;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.error.ApiException;
import com.foodmind.foodmindbackend.common.error.ErrorCode;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.inventory.api.request.InventoryLotRequest;
import com.foodmind.foodmindbackend.inventory.api.response.InventoryLotResponse;
import com.foodmind.foodmindbackend.inventory.application.ArchiveInventoryLot;
import com.foodmind.foodmindbackend.inventory.application.CreateInventoryLot;
import com.foodmind.foodmindbackend.inventory.application.GetInventoryLot;
import com.foodmind.foodmindbackend.inventory.application.ListInventoryLots;
import com.foodmind.foodmindbackend.inventory.application.UpdateInventoryLot;
import com.foodmind.foodmindbackend.inventory.domain.InventoryLot;
import com.foodmind.foodmindbackend.inventory.domain.InventoryLotPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/inventory/lots")
public class InventoryController {
    private final CreateInventoryLot create;
    private final GetInventoryLot get;
    private final ListInventoryLots list;
    private final UpdateInventoryLot update;
    private final ArchiveInventoryLot archive;

    public InventoryController(
            CreateInventoryLot create,
            GetInventoryLot get,
            ListInventoryLots list,
            UpdateInventoryLot update,
            ArchiveInventoryLot archive) {
        this.create = create;
        this.get = get;
        this.list = list;
        this.update = update;
        this.archive = archive;
    }

    @PostMapping
    ResponseEntity<InventoryLotResponse> create(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody InventoryLotRequest request) {
        InventoryLot lot = create.handle(principal.id(), request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/inventory/lots/" + lot.id()))
                .eTag(etag(lot.version()))
                .body(InventoryLotResponse.from(lot));
    }

    @GetMapping
    PageResponse<InventoryLotResponse> list(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_PAGE_SIZE) int size) {
        InventoryLotPage result = list.handle(principal.id(), page, size);
        return PageResponse.of(result.items().stream().map(InventoryLotResponse::from).toList(),
                page, size, result.totalItems());
    }

    @GetMapping("/{lotId}")
    ResponseEntity<InventoryLotResponse> get(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID lotId) {
        InventoryLot lot = get.handle(principal.id(), lotId);
        return ResponseEntity.ok().eTag(etag(lot.version())).body(InventoryLotResponse.from(lot));
    }

    @PutMapping("/{lotId}")
    ResponseEntity<InventoryLotResponse> update(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID lotId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody InventoryLotRequest request) {
        InventoryLot lot = update.handle(principal.id(), lotId, expectedVersion(ifMatch), request.toCommand());
        return ResponseEntity.ok().eTag(etag(lot.version())).body(InventoryLotResponse.from(lot));
    }

    @DeleteMapping("/{lotId}")
    ResponseEntity<Void> archive(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID lotId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        archive.handle(principal.id(), lotId, expectedVersion(ifMatch));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }

    private long expectedVersion(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "If-Match must contain the expected numeric version.");
        }
    }
}
