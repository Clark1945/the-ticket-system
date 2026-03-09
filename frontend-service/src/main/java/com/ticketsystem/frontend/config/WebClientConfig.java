package com.ticketsystem.frontend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${app.auth-service-url:http://localhost:8080}")
    private String authServiceUrl;

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000)
            .responseTimeout(Duration.ofSeconds(4))
            .doOnConnected(conn ->
                conn.addHandlerLast(new ReadTimeoutHandler(4, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(4, TimeUnit.SECONDS))
            );

        return WebClient.builder()
            .baseUrl(authServiceUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
