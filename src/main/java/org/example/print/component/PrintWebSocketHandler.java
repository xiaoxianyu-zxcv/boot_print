package org.example.print.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.print.service.LocalPrintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
@Slf4j
public class PrintWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(PrintWebSocketHandler.class);

    @Autowired
    private LocalPrintService localPrintService;  // 更新服务名称

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("新的WebSocket连接建立: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            logger.info("收到打印请求: {}", payload);

            // TODO: 实现打印逻辑
            // 将接收到的字符串解析为JSON对象
            // payload格式示例: {"data": [{打印任务1}, {打印任务2}]}
            JSONObject jsonData = JSON.parseObject(payload);

            // 获取data数组，包含多个打印任务
            // 每个打印任务格式示例: {"printerName": "打印机名称", "content": "打印内容"}
            JSONArray printDataArray = jsonData.getJSONArray("data");

            // 逐个处理打印任务
            for (int i = 0; i < printDataArray.size(); i++) {
                JSONObject printData = printDataArray.getJSONObject(i);
                localPrintService.print(printData);
            }


            // 先返回一个简单的确认消息
            //String response = "{\"status\":\"received\",\"message\":\"数据已接收\"}";



            // 发送打印状态回前端
            session.sendMessage(new TextMessage("{\"status\":\"success\",\"message\":\"打印请求已接收\"}"));
        } catch (Exception e) {
            logger.error("处理打印请求失败", e);
            try {
                session.sendMessage(new TextMessage("{\"status\":\"error\",\"message\":\"打印失败\"}"));
            } catch (IOException ex) {
                logger.error("发送错误消息失败", ex);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket连接关闭: {}, 状态: {}", session.getId(), status);
    }
}