package org.example.print.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP WebSocket 配置
 * 用于支持STOMP协议通信，主要为新版Electron客户端提供服务
 * 与原有的WebSocketConfig并存，提供双通道支持
 */
@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 配置消息代理，广播式应用通常以/topic为前缀
        config.enableSimpleBroker("/topic");
        // 配置客户端发送消息的前缀，这个前缀会由控制器方法的@MessageMapping注解处理
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册STOMP端点，使用/print-ws路径（与原有/print区分）
        // 支持SockJS降级选项，并设置心跳时间
        registry.addEndpoint("/print-ws")
                .setAllowedOriginPatterns("*")  // 替换setAllowedOrigins
                .withSockJS()
                .setHeartbeatTime(25000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024) // 64KB
                .setSendTimeLimit(20 * 1000)    // 20 seconds
                .setSendBufferSizeLimit(3 * 1024 * 1024); // 3MB
    }
}
