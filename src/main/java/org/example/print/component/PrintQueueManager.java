package org.example.print.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.example.print.service.EnhancedPrintService;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;



@Component
@Slf4j
public class PrintQueueManager {

    private final PrintQueue printQueue;
    private final EnhancedPrintService printService;


    @Value("${print.max-retry:3}")  // 添加配置项
    private int maxRetry;


    @Autowired
    public PrintQueueManager(PrintQueue printQueue, EnhancedPrintService printService) {
        this.printQueue = printQueue;
        this.printService = printService;
    }

    // 添加打印任务
    public void addPrintTask(PrintTask task) {
        task.setStatus(PrintTaskStatus.PENDING);
        task.setCreateTime(LocalDateTime.now());
        boolean added = printQueue.offer(task);  // 使用布尔值接收结果
        if (!added) {
            log.error("队列已满，无法添加任务: {}", task.getTaskId());
            throw new RuntimeException("打印队列已满");
        }
        log.info("添加打印任务到队列: {}", task.getTaskId());
    }

    // 定时处理打印任务
    @Scheduled(fixedRate = 1000)
    public void processPrintTasks() {
        // 从队列中取出任务
        PrintTask task = printQueue.poll();
        if (task != null) {
            try {
                task.setStatus(PrintTaskStatus.PRINTING);
                printService.executePrint(task);
                task.setStatus(PrintTaskStatus.COMPLETED);
                log.info("打印任务完成: {}", task.getTaskId());
            } catch (Exception e) {
                handleFailedTask(task);
            }
        }
    }

    // 处理失败任务
    private void handleFailedTask(PrintTask task) {
        task.setStatus(PrintTaskStatus.FAILED);
        task.setRetryCount(task.getRetryCount() + 1);

        if (task.getRetryCount() < maxRetry) {
            boolean requeued = printQueue.offer(task);  // 使用布尔值接收结果
            if (requeued) {
                log.info("打印任务重新入队: {}, 重试次数: {}", task.getTaskId(), task.getRetryCount());
            } else {
                log.error("重试入队失败: {}", task.getTaskId());
            }
        } else {
            log.error("打印任务达到最大重试次数: {}", task.getTaskId());
        }
    }
}
