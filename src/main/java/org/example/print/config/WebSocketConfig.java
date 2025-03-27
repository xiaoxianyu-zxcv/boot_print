package org.example.print.config;

import org.example.print.component.PrintWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * 原始WebSocket配置
 * 用于支持现有前端系统的WebSocket连接
 * 与新增的StompWebSocketConfig并存，支持双通道通信
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private PrintWebSocketHandler printWebSocketHandler;  // 直接注入原有的处理器

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 保持原有端点不变，继续处理/print路径
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