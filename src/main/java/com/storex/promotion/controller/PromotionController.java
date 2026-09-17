package com.storex.promotion.controller;

import com.storex.promotion.model.Banner;
import com.storex.promotion.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping("/banner")
    public Mono<Banner> getHomeBanner() {
        return promotionService.getActiveBanner();
    }
}
