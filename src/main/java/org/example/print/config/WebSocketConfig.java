package org.example.print.config;


import org.example.print.component.PrintWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;


@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {


    @Autowired
    private PrintWebSocketHandler printWebSocketHandler;  // 直接注入


    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(printWebSocketHandler, "/print")
                .setAllowedOrigins("*");  // 开发环境允许所有源，生产环境需要限制
    }


    @Bean
    public WebSocketTransportRegistration webSocketTransportRegistration() {
        return new WebSocketTransportRegistration()
                .setMessageSizeLimit(64 * 1024) // 64KB
                .setSendTimeLimit(20 * 1000)    // 20 seconds
                .setSendBufferSizeLimit(3 * 1024 * 1024); // 3MB
    }



}

