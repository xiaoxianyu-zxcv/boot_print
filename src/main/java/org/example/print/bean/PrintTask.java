package org.example.print.bean;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PrintTask {
    private String taskId;                 // 任务ID
    private String content;                // 打印内容
    private PrintTaskStatus status;        // 任务状态
    private int retryCount;                // 重试次数
    private LocalDateTime createTime;      // 创建时间
    private String printerName;            // 打印机名称
    private PrintTaskPriority priority;     // 任务优先级

    // 新增STOMP相关字段
    private String messageId;              // STOMP消息ID
    private String type;                   // 消息类型
    private String message;                // 状态消息
}


