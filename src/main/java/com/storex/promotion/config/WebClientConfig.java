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

/**
 * Cấu hình WebClient chuẩn Non-blocking trong Spring WebFlux:
 * - Tích hợp Netty ReactorClientHttpConnector với các timeout tầng socket/TCP.
 * - @LoadBalanced WebClient.Builder cho phép phân giải Logical Service ID qua Eureka.
 * - Cấu hình timeout kết nối (connectTimeout = 2s) và thời gian phản hồi (responseTimeout = 2s).
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                // Cấu hình TCP Connect Timeout: 2000ms
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                // Cấu hình Netty Response Timeout: 2s
                .responseTimeout(Duration.ofSeconds(2))
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
