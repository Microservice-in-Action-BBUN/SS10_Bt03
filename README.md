# BÀI TẬP 3: CHUYỂN ĐỔI RESTTEMPLATE SANG WEBCLIENT TRONG SPRING WEBFLUX
## Tối Ưu Hóa API Banner Khuyến Mãi StoreX Chịu Tải 10.000 Concurrent Users

> **Mã bài tập:** `SPRING-WEBFLUX-S10-EX03`  
> **Khóa học:** Microservices System Design — Session 10: Rikkei Education  
> **Cấp độ:** Vận dụng  
> **Dự án:** Sàn Thương mại Điện tử StoreX (**StoreX E-Commerce Platform**)  
> **Module:** `storex-home-service` $\rightarrow$ `promotion-service`  
> **Công nghệ áp dụng:** Spring Boot 3.x, Spring WebFlux, Project Reactor (`Mono`), Netty, WebClient, StepVerifier  

---

## 1. Bối Cảnh Nghiệp Vụ & Hiện Trạng Sự Cố

Hệ thống **StoreX** có một API tại trang chủ hiển thị **Banner Khuyến mãi hiện hành** cho khách hàng. API này cần gọi sang dịch vụ nội bộ `promotion-service` để lấy banner mới nhất. Yêu cầu phi chức năng đặt ra là hệ thống phải chịu tải tối thiểu **10.000 người dùng truy cập đồng thời** trong dịp siêu hội mua sắm Flash Sale.

Một kỹ sư cũ đã để lại đoạn code dưới đây. Dù dự án đã được khai báo phụ thuộc `spring-boot-starter-webflux`, nhưng khi tiến hành chạy kiểm thử chịu tải (Stress/Load Test), hệ thống liên tục trả về mã lỗi **HTTP 500 Internal Server Error**, toàn bộ server bị treo cứng và cạn kiệt tài nguyên luồng:

```java
// PromotionService.java -- CODE ĐANG GẶP LỖI NGHIÊM TRỌNG
@Service
public class PromotionService {

    // SAI 1: Dùng RestTemplate trong WebFlux -- RestTemplate là BLOCKING
    private final RestTemplate restTemplate = new RestTemplate();

    public Banner getActiveBanner() {
        // SAI 2: Gọi đồng bộ, block thread
        String url = "http://promotion-service/api/banners/active";
        Banner banner = restTemplate.getForObject(url, Banner.class);

        // SAI 3: Nếu service chết, hệ thống crash
        if (banner == null) {
            throw new RuntimeException("Promotion service unavailable");
        }
        return banner;
    }
}
```

---

## 2. Phần 1 - Phân Tích Chuyên Sâu Lỗi Kiến Trúc

### 2.1. Cơ chế hoạt động của RestTemplate & Nguyên nhân gây cạn kiệt Thread
* **Mô hình Synchronous Blocking I/O (Chặn đồng bộ):**
  * `RestTemplate` được xây dựng dựa trên Java Servlet API truyền thống (từ Spring 3.0). Khi một phương thức như `getForObject()` được gọi, luồng (thread) đang thực thi sẽ phát tín hiệu gửi socket ra tầng mạng và chuyển ngay sang trạng thái **`WAITING` / `BLOCKED`**.
  * Luồng này bị "đóng băng" hoàn toàn, không thể làm bất kỳ công việc nào khác (kể cả nhận request mới) cho đến khi toàn bộ gói tin HTTP Response được máy chủ đích trả về đầy đủ qua socket.
  * Trong mô hình Tomcat Servlet truyền thống, mỗi request chiếm 1 thread riêng trong pool 200 threads (**Thread-per-Request**). Khi có 10.000 người dùng truy cập đồng thời, hệ thống cần ít nhất hàng ngàn thread. Chi phí chuyển ngữ cảnh (Context Switching) và tiêu tốn bộ nhớ ngăn xếp (Stack Memory: mỗi thread tốn ~1MB RAM) sẽ nhanh chóng làm kiệt quệ tài nguyên máy chủ.

---

### 2.2. Tại sao WebFlux không thể xử lý Non-blocking khi có RestTemplate?
* **Kiến trúc Reactor Netty (EventLoop Model):**
  * Khác biệt căn bản với Tomcat, Spring WebFlux vận hành dựa trên máy chủ **Reactor Netty** theo mô hình **Event-Driven Non-blocking I/O**.
  * Netty chỉ duy trì một số lượng thread cực kỳ khiêm tốn (**EventLoop Threads**), mặc định được tính theo công thức:
    $$\text{Số EventLoop Threads} = 2 \times \text{Số CPU Cores}$$
    *(Ví dụ: Máy chủ có 4 CPU Cores thì Netty chỉ cấp đúng **8 Worker Threads**).*
  * Mỗi EventLoop thread chịu trách nhiệm giám sát hàng chục ngàn kết nối đồng thời thông qua cơ chế Selector/Channel của Java NIO. Khi một request gửi dữ liệu I/O qua mạng, luồng Netty không hề chờ mà ngay lập tức quay sang phục vụ request khác. Khi có dữ liệu trả về, một sự kiện (Event) được bắn ra để tiếp tục xử lý.

* **Hiệu ứng "Đầu độc EventLoop" (Poisoning the EventLoop):**
  * Quy tắc vàng và bất biến của Reactive Programming là: **"NEVER BLOCK THE EVENT LOOP"** (Không bao giờ được phép chặn luồng EventLoop).
  * Khi đoạn mã cũ gọi `restTemplate.getForObject(...)`, nó đang chạy trực tiếp trên một trong 8 EventLoop threads của Netty.
  * Việc này khiến luồng Netty đó bị **khóa cứng (Blocked)** để chờ phản hồi từ `promotion-service`.
  * **Hậu quả thảm khốc khi có 10.000 users:** Chỉ cần **8 người dùng đầu tiên** gửi request và `promotion-service` bị trễ mạng khoảng 1-2 giây, toàn bộ **8 Netty Worker Threads đều bị khóa cứng 100%**.
  * Lúc này, Netty không còn bất kỳ thread nào rảnh rỗi để tiếp nhận hay xử lý các sự kiện mạng I/O của 9.992 người dùng còn lại. Toàn bộ ứng dụng WebFlux bị **"đóng băng" (Freeze)**, thời gian phản hồi tăng vọt, các kết nối bị từ chối hoặc timeout, dẫn đến hàng loạt lỗi **HTTP 500 / HTTP 504** và hệ thống crash hoàn toàn.

---

### 2.3. Bảng đối chiếu kiến trúc giữa RestTemplate và WebClient

| Tiêu chí | `RestTemplate` (Mô hình cũ) | `WebClient` (Chuẩn WebFlux Reactive) |
| :--- | :--- | :--- |
| **Cơ chế I/O** | Synchronous Blocking I/O | Asynchronous Non-blocking I/O |
| **Mô hình luồng (Thread Model)** | Khóa cứng thread chờ socket (`Thread-per-Request`) | Sử dụng EventLoop (Netty), không block thread |
| **Số lượng Thread cần thiết** | Cần hàng ngàn thread cho 10.000 concurrent users | Chỉ cần 4 - 8 thread vẫn chịu tải mượt mà 10.000 users |
| **Kiểu dữ liệu trả về** | Đối tượng tĩnh (`Banner`) | Luồng phản ứng bất đồng bộ (`Mono<Banner>`) |
| **Khả năng xử lý Timeout** | Khó tùy biến, dễ treo socket vô hạn | Tích hợp sẵn toán tử `.timeout(Duration)` của Reactor |
| **Phục hồi sự cố (Resilience)** | Ném `RuntimeException` làm sập luồng gọi | `.onErrorResume(...)` suy thoái mượt mà (Fallback) |

---

## 3. Phần 2 - Mã Nguồn Sửa Đổi Chuẩn Reactive

### 3.1. Cấu hình WebClient với Timeout (`WebClientConfig.java`)
Cấu hình Netty ClientConnector thiết lập TCP Connect Timeout (2s) và Response Timeout (2s):

```java
package com.storex.promotion.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000) // connectTimeout = 2s
                .responseTimeout(Duration.ofSeconds(2))             // responseTimeout = 2s
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(2, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(2, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean
    public WebClient promotionWebClient(
            WebClient.Builder webClientBuilder,
            @Value("${services.promotion-service.url:http://promotion-service}") String baseUrl
    ) {
        return webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }
}
```

---

### 3.2. Lớp Dịch Vụ Reactive Đã Sửa Đổi (`PromotionService.java`)
```java
package com.storex.promotion.service;

import com.storex.promotion.model.Banner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
public class PromotionService {

    private final WebClient webClient;
    private static final Duration TIMEOUT_DURATION = Duration.ofSeconds(2);

    @Autowired
    public PromotionService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<Banner> getActiveBanner() {
        log.info("Gửi yêu cầu Non-blocking GET tới /api/banners/active qua WebClient");

        return webClient.get()
                .uri("/api/banners/active")
                .retrieve()
                .bodyToMono(Banner.class)
                // Cấu hình Timeout 2 giây: Nếu dịch vụ phản hồi trễ > 2s, ngắt kết nối
                .timeout(TIMEOUT_DURATION)
                .doOnSuccess(banner -> {
                    if (banner != null) {
                        log.info("Nhận thành công banner: {}", banner.getTitle());
                    }
                })
                .doOnError(error -> log.warn("Sự cố gọi promotion-service [{}]: {}. Kích hoạt Fallback.",
                        error.getClass().getSimpleName(), error.getMessage()))
                // Xử lý Fallback: Nếu quá 2s timeout hoặc service lỗi (500/503), trả về banner mặc định
                .onErrorResume(error -> Mono.just(Banner.defaultBanner()))
                // Tránh lỗi null khi body trả về rỗng (HTTP 204)
                .defaultIfEmpty(Banner.defaultBanner());
    }
}
```

---

### 3.3. Đối Tượng Model & Fallback (`Banner.java`)
```java
package com.storex.promotion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public static Banner defaultBanner() {
        return Banner.builder()
                .id(0L)
                .title("Thông Báo Khuyến Mãi")
                .content("Khuyến mãi đang được cập nhật") // Đúng yêu cầu đề bài
                .imageUrl("/images/banners/default-banner.png")
                .targetUrl("/")
                .status("UPDATING")
                .build();
    }
}
```

---

## 4. Kết Quả Kiểm Thử Đơn Vị (Unit Test) Với StepVerifier

Bộ test trong `PromotionServiceTest.java` sử dụng công cụ kiểm thử reactive chuẩn mực `StepVerifier` của Project Reactor:

1. **Happy Path (`testGetActiveBanner_Success`)**: Mô phỏng nhận banner thật từ `promotion-service`. Xác nhận `StepVerifier` nhận đúng `Banner` và kết thúc luồng (`verifyComplete`).
2. **Timeout Simulation (`testGetActiveBanner_Timeout_ReturnsDefaultBanner`)**: Mô phỏng lỗi `TimeoutException` khi máy chủ phản hồi quá 2 giây. Xác nhận hệ thống không bị crash mà trả về `Banner.defaultBanner()` chứa thông báo **"Khuyến mãi đang được cập nhật"**.
3. **Service Unavailable (`testGetActiveBanner_ServiceError_ReturnsDefaultBanner`)**: Mô phỏng lỗi HTTP 503 / 500, xác nhận kích hoạt banner dự phòng an toàn.
4. **Empty Body (`testGetActiveBanner_EmptyBody_ReturnsDefaultBanner`)**: Mô phỏng trả về `Mono.empty()`, xác nhận tự động điền banner mặc định.

### Kết quả chạy `./gradlew test`:
```text
BUILD SUCCESSFUL in 2s
3 actionable tasks: 3 executed
Test summary: 4 passed, 0 failed, 0 skipped
```
Toàn bộ 4 Unit Test đều vượt qua với trạng thái **PASSED (100%)**.
