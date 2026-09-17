package com.storex.promotion.service;

import com.storex.promotion.model.Banner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private PromotionService promotionService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        promotionService = new PromotionService(webClient);

        lenient().when(webClient.get()).thenReturn(requestHeadersUriSpec);
        lenient().when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    @DisplayName("Unit Test 1: Happy Path - Lấy thành công Banner khuyến mãi đang kích hoạt")
    void testGetActiveBanner_Success() {
        // Arrange
        Banner realBanner = Banner.builder()
                .id(101L)
                .title("Đại Tiệc Flash Sale StoreX 9.9")
                .content("Giảm giá lên đến 50% toàn bộ sản phẩm công nghệ")
                .imageUrl("/images/flash-sale-99.png")
                .targetUrl("/campaigns/flash-sale-99")
                .status("ACTIVE")
                .build();

        when(responseSpec.bodyToMono(Banner.class)).thenReturn(Mono.just(realBanner));

        // Act & Assert với StepVerifier của Project Reactor
        StepVerifier.create(promotionService.getActiveBanner())
                .expectNextMatches(banner -> {
                    return banner.getId().equals(101L)
                            && "Đại Tiệc Flash Sale StoreX 9.9".equals(banner.getTitle())
                            && "ACTIVE".equals(banner.getStatus())
                            && "/campaigns/flash-sale-99".equals(banner.getTargetUrl());
                })
                .verifyComplete();

        verify(webClient, times(1)).get();
    }

    @Test
    @DisplayName("Unit Test 2: Timeout Simulation - Xử lý Timeout sau 2s và trả về Banner mặc định")
    void testGetActiveBanner_Timeout_ReturnsDefaultBanner() {
        // Arrange: Mô phỏng dịch vụ promotion-service bị trễ mạng và ném TimeoutException
        when(responseSpec.bodyToMono(Banner.class))
                .thenReturn(Mono.error(new TimeoutException("Connection or read timed out after 2000ms")));

        // Act & Assert: Xác minh StepVerifier nhận được banner mặc định an toàn
        StepVerifier.create(promotionService.getActiveBanner())
                .expectNextMatches(banner -> {
                    return banner.getId().equals(0L)
                            && "Khuyến mãi đang được cập nhật".equals(banner.getContent())
                            && "UPDATING".equals(banner.getStatus());
                })
                .verifyComplete();

        verify(webClient, times(1)).get();
    }

    @Test
    @DisplayName("Unit Test 3: Service Unavailable (503/500) - Bắt lỗi mạng và trả về Fallback an toàn")
    void testGetActiveBanner_ServiceError_ReturnsDefaultBanner() {
        // Arrange: Mô phỏng lỗi HTTP 503 Service Unavailable từ promotion-service
        when(responseSpec.bodyToMono(Banner.class))
                .thenReturn(Mono.error(new WebClientResponseException(503, "Service Unavailable", null, null, null)));

        // Act & Assert: Hệ thống không crash mà trả về banner mặc định
        StepVerifier.create(promotionService.getActiveBanner())
                .expectNextMatches(banner -> {
                    return "Khuyến mãi đang được cập nhật".equals(banner.getContent())
                            && "UPDATING".equals(banner.getStatus());
                })
                .verifyComplete();

        verify(webClient, times(1)).get();
    }

    @Test
    @DisplayName("Unit Test 4: Empty Body - Khi Promotion Service trả về rỗng, tự động điền Default Banner")
    void testGetActiveBanner_EmptyBody_ReturnsDefaultBanner() {
        // Arrange: Trả về Mono.empty() (ví dụ HTTP 204 No Content)
        when(responseSpec.bodyToMono(Banner.class)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(promotionService.getActiveBanner())
                .expectNextMatches(banner -> "Khuyến mãi đang được cập nhật".equals(banner.getContent()))
                .verifyComplete();

        verify(webClient, times(1)).get();
    }
}
