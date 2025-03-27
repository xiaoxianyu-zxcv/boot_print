package org.example.print.service;

import lombok.extern.slf4j.Slf4j;
import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.example.print.component.PrintQueueManager;
import org.example.print.controller.PrintMessageController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import javax.annotation.PostConstruct;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 远程数据服务 (STOMP版本)
 * 负责与远程服务器通信，获取打印任务数据
 */
@Service
@Slf4j
public class RemoteDataService {

    private final PrintQueueManager printQueueManager;
    private final RestTemplate restTemplate;
    private final PrintMessageController printMessageController;

    private StompSession stompSession;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    // 远程服务器配置
    @Value("${remote.server.url:http://localhost:9090}")
    private String serverUrl;

    @Value("${remote.server.ws-path:/print-ws}")
    private String wsPath;

    @Value("${remote.auth.enabled:false}")
    private boolean authEnabled;

    @Value("${remote.auth.username:}")
    private String username;

    @Value("${remote.auth.password:}")
    private String password;

    @Value("${remote.connection.retry-interval:5000}")
    private long retryInterval;

    @Autowired
    public RemoteDataService(
            PrintQueueManager printQueueManager,
            PrintMessageController printMessageController) {
        this.printQueueManager = printQueueManager;
        this.printMessageController = printMessageController;
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void initialize() {
        // 连接WebSocket
        connectStompClient();

        // 初始同步一次打印任务
        syncPrintTasks();
    }

    /**
     * 连接STOMP客户端
     */
    private void connectStompClient() {
        if (isConnected.get()) {
            return;
        }

        try {
            log.info("正在连接STOMP服务: {}", serverUrl + wsPath);

            // 创建WebSocket客户端
            List<Transport> transports = Collections.singletonList(new WebSocketTransport(new StandardWebSocketClient()));
            WebSocketClient client = new SockJsClient(transports);

            // 创建STOMP客户端
            WebSocketStompClient stompClient = new WebSocketStompClient(client);
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());

            // 连接STOMP服务器
            String stompUrl = serverUrl + wsPath;

            // 创建STOMP会话处理器
            StompSessionHandler sessionHandler = new StompSessionHandler() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    stompSession = session;
                    isConnected.set(true);
                    log.info("STOMP连接已建立");

                    // 订阅打印主题
                    session.subscribe("/topic/print-tasks", this);
                    log.info("已订阅打印任务主题");

                    // 发送身份验证消息
                    if (authEnabled) {
                        Map<String, String> authMessage = new HashMap<>();
                        authMessage.put("type", "auth");
                        authMessage.put("username", username);
                        authMessage.put("password", password);
                        session.send("/app/auth", authMessage);
                        log.info("已发送身份验证信息");
                    }
                }

                @Override
                public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                    log.error("STOMP处理异常", exception);
                    isConnected.set(false);
                    scheduleReconnect();
                }

                @Override
                public void handleTransportError(StompSession session, Throwable exception) {
                    log.error("STOMP传输错误", exception);
                    isConnected.set(false);
                    scheduleReconnect();
                }

                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    if (payload instanceof Map) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> message = (Map<String, Object>) payload;
                            log.debug("收到STOMP消息: {}", message);

                            // 处理打印任务消息
                            if ("print_task".equals(message.get("type"))) {
                                // 转换为PrintTask对象
                                PrintTask task = convertToPrintTask(message);

                                // 添加到打印队列
                                printQueueManager.addPrintTask(task);
                                log.info("已从STOMP接收并添加打印任务: {}", task.getTaskId());
                            }
                        } catch (Exception e) {
                            log.error("处理STOMP消息失败", e);
                        }
                    }
                }
            };

            // 连接STOMP服务器
            stompClient.connect(stompUrl, sessionHandler);

        } catch (Exception e) {
            log.error("连接STOMP服务失败", e);
            // 安排重连
            scheduleReconnect();
        }
    }

    /**
     * 将消息转换为PrintTask对象
     */
    private PrintTask convertToPrintTask(Map<String, Object> message) {
        String taskId = message.containsKey("taskId") ?
                (String) message.get("taskId") : UUID.randomUUID().toString();

        String content = message.containsKey("content") ?
                (String) message.get("content") : "";

        String printerName = message.containsKey("printerName") ?
                (String) message.get("printerName") : null;

        return PrintTask.builder()
                .taskId(taskId)
                .content(content)
                .status(PrintTaskStatus.PENDING)
                .createTime(LocalDateTime.now())
                .retryCount(0)
                .printerName(printerName)
                .build();
    }

    /**
     * 安排STOMP重连
     */
    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                log.info("计划在{}毫秒后重新连接STOMP", retryInterval);
                Thread.sleep(retryInterval);
                connectStompClient();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("STOMP重连线程被中断", e);
            }
        }).start();
    }

    /**
     * 定期同步打印任务
     * 作为WebSocket的备份机制
     */
    @Scheduled(fixedDelayString = "${remote.poll.interval:60000}")
    public void syncPrintTasks() {
        try {
            log.debug("开始同步打印任务");
            List<PrintTask> tasks = fetchPrintTasks();

            if (!tasks.isEmpty()) {
                log.info("从服务器获取到{}个打印任务", tasks.size());
                for (PrintTask task : tasks) {
                    // 确保任务状态和时间设置正确
                    task.setStatus(PrintTaskStatus.PENDING);
                    if (task.getCreateTime() == null) {
                        task.setCreateTime(LocalDateTime.now());
                    }

                    // 添加到打印队列
                    printQueueManager.addPrintTask(task);

                    // 可选：通知服务器任务已接收
                    notifyTaskReceived(task.getTaskId());
                }
            }
        } catch (Exception e) {
            log.error("同步打印任务失败", e);
        }
    }

    /**
     * 从服务器获取待处理的打印任务
     */
    public List<PrintTask> fetchPrintTasks() {
        try {
            String url = serverUrl + "/api/print-tasks/pending";
            log.debug("正在从{}获取打印任务", url);

            HttpEntity<?> requestEntity = createAuthenticatedRequest();

            ResponseEntity<List<PrintTask>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<List<PrintTask>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.warn("获取打印任务返回非成功状态码: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("获取打印任务失败", e);
        }

        return Collections.emptyList();
    }

    /**
     * 通知服务器打印任务已接收
     */
    private void notifyTaskReceived(String taskId) {
        try {
            String url = serverUrl + "/api/print-tasks/" + taskId + "/received";
            HttpEntity<?> requestEntity = createAuthenticatedRequest();

            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    Void.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("已通知服务器任务已接收: {}", taskId);
            } else {
                log.warn("通知服务器任务已接收失败: {}, 状态码: {}",
                        taskId, response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("通知服务器任务已接收失败: {}", taskId, e);
        }
    }

    /**
     * 创建带认证的请求
     */
    private HttpEntity<?> createAuthenticatedRequest() {
        HttpHeaders headers = new HttpHeaders();

        // 添加认证信息
        if (authEnabled) {
            // 这里简化处理，实际中应该使用更安全的认证方式
            String auth = username + ":" + password;
            headers.set("Authorization", "Basic " +
                    java.util.Base64.getEncoder().encodeToString(auth.getBytes()));
        }

        return new HttpEntity<>(headers);
    }

    /**
     * 更新打印任务状态到服务器
     */
    public void updateTaskStatus(String taskId, PrintTaskStatus status) {
        try {
            // 通过REST API更新状态
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

            // 通过WebSocket推送状态更新
            if (isConnected.get() && stompSession != null && stompSession.isConnected()) {
                Map<String, Object> statusUpdate = new HashMap<>();
                statusUpdate.put("taskId", taskId);
                statusUpdate.put("status", status.name());

                stompSession.send("/app/task-status", statusUpdate);
            }

            // 本地推送状态更新
            PrintTask task = new PrintTask();
            task.setTaskId(taskId);
            task.setStatus(status);
            printMessageController.sendPrintStatusUpdate(task);

        } catch (Exception e) {
            log.error("更新服务器任务状态失败: {}, 状态: {}", taskId, status, e);
        }
    }

    /**
     * 检查服务器连接状态
     */
    public boolean isServerConnected() {
        return isConnected.get() && stompSession != null && stompSession.isConnected();
    }
}