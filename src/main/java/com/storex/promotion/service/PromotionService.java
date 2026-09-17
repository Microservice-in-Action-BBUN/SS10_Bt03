package com.storex.promotion.service;

import com.storex.promotion.model.Banner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * PromotionService - Triển khai chuẩn Non-blocking Reactive với Spring WebFlux:
 * 1. Thay thế RestTemplate bằng WebClient để giải phóng hoàn toàn Netty EventLoop Threads.
 * 2. Trả về Mono<Banner> biểu diễn dòng dữ liệu bất đồng bộ không chặn (Non-blocking Reactive Stream).
 * 3. Cấu hình Timeout 2 giây: Nếu promotion-service phản hồi chậm > 2s, ngắt kết nối ngay lập tức.
 * 4. Cơ chế Fallback an toàn: Khi xảy ra Timeout, lỗi mạng hoặc dịch vụ đích ngừng hoạt động (503/500),
 *    hệ thống không bao giờ bị Crash mà trả về Banner mặc định với thông báo "Khuyến mãi đang được cập nhật".
 */
@Service
@Slf4j
public class PromotionService {

    private final WebClient webClient;
    private static final Duration TIMEOUT_DURATION = Duration.ofSeconds(2);

    @Autowired
    public PromotionService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Lấy Banner khuyến mãi đang kích hoạt từ promotion-service theo cơ chế Non-blocking.
     *
     * @return Mono<Banner> Luồng dữ liệu Reactive chứa Banner thật hoặc Banner mặc định dự phòng.
     */
    public Mono<Banner> getActiveBanner() {
        log.info("Gửi yêu cầu Non-blocking GET tới /api/banners/active qua WebClient");

        return webClient.get()
                .uri("/api/banners/active")
                .retrieve()
                .bodyToMono(Banner.class)
                // Cấu hình Timeout 2 giây theo yêu cầu bài toán
                .timeout(TIMEOUT_DURATION)
                .doOnSuccess(banner -> {
                    if (banner != null) {
                        log.info("Nhận thành công banner khuyến mãi: {}", banner.getTitle());
                    }
                })
                .doOnError(error -> log.warn("Xảy ra sự cố khi gọi promotion-service [{}]: {}. Kích hoạt Fallback.",
                        error.getClass().getSimpleName(), error.getMessage()))
                // Bắt mọi lỗi (TimeoutException, WebClientResponseException, ConnectException,...) để kích hoạt Fallback
                .onErrorResume(error -> Mono.just(Banner.defaultBanner()))
                // Trường hợp nhận dữ liệu rỗng (Empty body), tự động điền Banner mặc định
                .defaultIfEmpty(Banner.defaultBanner());
    }
}
