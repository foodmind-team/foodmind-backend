package com.foodmind.foodmindbackend.wanttotry.api;

import com.foodmind.foodmindbackend.common.api.PageResponse;
import com.foodmind.foodmindbackend.common.security.FoodMindPrincipal;
import com.foodmind.foodmindbackend.wanttotry.api.request.SaveWantToTryRequest;
import com.foodmind.foodmindbackend.wanttotry.api.response.WantToTryResponse;
import com.foodmind.foodmindbackend.wanttotry.application.DeleteWantToTry;
import com.foodmind.foodmindbackend.wanttotry.application.ListWantToTry;
import com.foodmind.foodmindbackend.wanttotry.application.SaveWantToTry;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTryItem;
import com.foodmind.foodmindbackend.wanttotry.domain.WantToTryPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

@Validated
@RestController
@RequestMapping("/api/v1/want-to-try")
public class WantToTryController {

    private final SaveWantToTry saveWantToTry;
    private final ListWantToTry listWantToTry;
    private final DeleteWantToTry deleteWantToTry;

    public WantToTryController(
            SaveWantToTry saveWantToTry,
            ListWantToTry listWantToTry,
            DeleteWantToTry deleteWantToTry) {
        this.saveWantToTry = saveWantToTry;
        this.listWantToTry = listWantToTry;
        this.deleteWantToTry = deleteWantToTry;
    }

    @PostMapping
    ResponseEntity<WantToTryResponse> create(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @Valid @RequestBody SaveWantToTryRequest request) {
        WantToTryItem item = saveWantToTry.handle(principal.id(), request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/want-to-try/" + item.id()))
                .body(WantToTryResponse.from(item));
    }

    @GetMapping
    PageResponse<WantToTryResponse> list(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageResponse.MAX_PAGE_SIZE) int size) {
        WantToTryPage result = listWantToTry.handle(principal.id(), page, size);
        return PageResponse.of(result.items().stream().map(WantToTryResponse::from).toList(), page, size, result.totalItems());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal FoodMindPrincipal principal,
            @PathVariable UUID id) {
        deleteWantToTry.handle(principal.id(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
