package com.storex.promotion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Model đại diện cho Banner Khuyến mãi hiển thị tại trang chủ StoreX.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {
    private Long id;
    private String title;
    private String content;
    private String imageUrl;
    private String targetUrl;
    private String status;

    /**
     * Trả về banner dự phòng mặc định khi dịch vụ promotion-service gặp sự cố hoặc timeout:
     * - Đáp ứng yêu cầu bài toán: Thông báo "Khuyến mãi đang được cập nhật".
     */
    public static Banner defaultBanner() {
        return Banner.builder()
                .id(0L)
                .title("Thông Báo Khuyến Mãi")
                .content("Khuyến mãi đang được cập nhật")
                .imageUrl("/images/banners/default-banner.png")
                .targetUrl("/")
                .status("UPDATING")
                .build();
    }
}
