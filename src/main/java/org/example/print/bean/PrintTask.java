package org.example.print.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor  // 添加默认构造函数
@AllArgsConstructor // 添加全参构造函数
public class PrintTask {
    private String taskId;                 // 任务ID
    private String content;                // 打印内容
    private PrintTaskStatus status;        // 任务状态
    private int retryCount;                // 重试次数
    private LocalDateTime createTime;      // 创建时间
    private String printerName;            // 打印机名称
    private PrintTaskPriority priority;     // 任务优先级

}


