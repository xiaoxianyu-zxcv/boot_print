package org.example.print.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class PrintWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(PrintWebSocketHandler.class);

    @Autowired
    private PrintQueueManager printQueueManager;


    // 心跳间隔时间（毫秒）
    private static final long HEARTBEAT_INTERVAL = 30000;


    // 存储所有活动的WebSocket会话及其心跳任务
    private final ConcurrentHashMap<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();

    @Lazy
    @Autowired
    private ScheduledExecutorService scheduledExecutor;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("新的WebSocket连接建立: {}", session.getId());
        startHeartbeat(session);
    }


    private void startHeartbeat(WebSocketSession session) {
        // scheduledExecutor 是一个定时任务执行器
        ScheduledFuture<?> heartbeatTask = scheduledExecutor.scheduleAtFixedRate(
                () -> sendHeartbeat(session),  // 要执行的任务
                0,                             // 首次执行延迟
                HEARTBEAT_INTERVAL,            // 之后每次执行的间隔（30秒）
                TimeUnit.MILLISECONDS          // 时间单位
        );
        // 将任务保存到Map中
        heartbeatTasks.put(session.getId(), heartbeatTask);
    }


    private void sendHeartbeat(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage("{\"type\":\"ping\"}"));
            } else {
                cancelHeartbeat(session.getId());
            }
        } catch (IOException e) {
            log.error("发送心跳消息失败: {}", session.getId(), e);
            cancelHeartbeat(session.getId());
        }
    }

    private void cancelHeartbeat(String sessionId) {
        ScheduledFuture<?> task = heartbeatTasks.remove(sessionId);
        if (task != null) {
            task.cancel(true);
        }
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket连接关闭: {}, 状态: {}", session.getId(), status);
        cancelHeartbeat(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        // 处理心跳响应
        if ("{\"type\":\"pong\"}".equals(payload)) {
            return;
        }

        try {

            logger.info("收到打印请求: {}", payload);

            // 将接收到的字符串解析为JSON对象
            // payload格式示例: {"data": [{打印任务1}, {打印任务2}]}
            JSONObject jsonData = JSON.parseObject(payload);

            // 获取data数组，包含多个打印任务
            // 每个打印任务格式示例: {"printerName": "打印机名称", "content": "打印内容"}
            JSONArray printDataArray = jsonData.getJSONArray("data");

            // 逐个处理打印任务
            for (int i = 0; i < printDataArray.size(); i++) {
                JSONObject printData = printDataArray.getJSONObject(i);
                // 创建打印任务
                PrintTask task = PrintTask.builder()
                        .taskId(UUID.randomUUID().toString())
                        //todo打印两份
                        .content(printData.toJSONString())
                        .status(PrintTaskStatus.PENDING)
                        .createTime(LocalDateTime.now())
                        .retryCount(0)
                        .printerName("GP-C58 Series")
                        .build();
                try {
                    // 添加到打印队列
                    printQueueManager.addPrintTask(task);

                    // 发送接收确认
                    session.sendMessage(new TextMessage("{\"type\":\"success\",\"message\":\"打印成功\"}"));
                } catch (Exception e) {
                    log.error("打印失败", e);
                    // 发送打印状态回前端
                    session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"打印失败: " + e.getMessage() + "\"}"));
                }
            }
        } catch (Exception e) {
            log.error("处理打印请求失败", e);
            try {
                session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"处理打印请求失败\"}"));
            } catch (IOException ex) {
                log.error("发送错误消息失败", ex);
            }
        }
    }

}
