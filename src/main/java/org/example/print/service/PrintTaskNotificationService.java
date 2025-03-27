package org.example.print.service;

import lombok.extern.slf4j.Slf4j;
import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 打印任务通知服务
 * 负责将打印任务状态变更通知给WebSocket客户端和远程服务器
 */
@Service
@Slf4j
public class PrintTaskNotificationService {
    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate;

    @Value("${remote.server.url:http://localhost:9090}")
    private String serverUrl;

    @Value("${remote.auth.enabled:false}")
    private boolean authEnabled;

    @Value("${remote.auth.username:}")
    private String username;

    @Value("${remote.auth.password:}")
    private String password;

    @Autowired
    public PrintTaskNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 向WebSocket客户端发送状态更新
     */
    public void notifyClient(PrintTask task) {
        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("taskId", task.getTaskId());
        statusUpdate.put("status", task.getStatus().name());
        statusUpdate.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/print-status", statusUpdate);
        log.debug("已发送任务状态更新到客户端: {}", task.getTaskId());
    }

    /**
     * 向远程服务器发送状态更新
     */
    public void notifyRemoteServer(String taskId, PrintTaskStatus status) {
        try {
            String url = serverUrl + "/api/print-tasks/" + taskId + "/status";

            HttpHeaders headers = new HttpHeaders();
            if (authEnabled) {
                String auth = username + ":" + password;
                headers.set("Authorization", "Basic " +
                        java.util.Base64.getEncoder().encodeToString(auth.getBytes()));
            }

            HttpEntity<String> requestEntity = new HttpEntity<>(status.name(), headers);

            restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    requestEntity,
                    Void.class
            );

            log.debug("已发送任务状态更新到远程服务器: {}", taskId);
        } catch (Exception e) {
            log.error("更新服务器任务状态失败: {}, 状态: {}", taskId, status, e);
        }
    }

    /**
     * 同时通知客户端和远程服务器
     */
    public void notifyAll(PrintTask task) {
        notifyClient(task);
        notifyRemoteServer(task.getTaskId(), task.getStatus());
    }

    /**
     * 发送WebSocket消息
     */
    public void sendWebSocketMessage(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }
}
