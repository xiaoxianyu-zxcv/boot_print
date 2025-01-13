package org.example.print.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.example.print.service.EnhancedPrintService;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;


@Component
@Slf4j
public class PrintQueueManager {

    private final PrintQueue printQueue;
    private final EnhancedPrintService printService;
    private final Executor taskExecutor;


    @Value("${print.max-retry:3}")  // 添加配置项
    private int maxRetry;

    @Value("${print.queue.offer-timeout:3}")  // 配置超时时间
    private int offerTimeout;


    @Autowired
    public PrintQueueManager(PrintQueue printQueue,
                             EnhancedPrintService printService,
                             @Qualifier("printTaskExecutor")Executor  taskExecutor) {
        this.printQueue = printQueue;
        this.printService = printService;
        this.taskExecutor = taskExecutor;
    }

    // 添加打印任务
    public void addPrintTask(PrintTask task) {
        task.setStatus(PrintTaskStatus.PENDING);
        task.setCreateTime(LocalDateTime.now());


        try {
            // 使用带超时的offer，给一个短暂的等待时间
            boolean added = printQueue.offer(task, offerTimeout, TimeUnit.SECONDS);
            if (!added) {
                log.error("队列已满，无法添加任务: {}, 当前队列大小: {}",
                        task.getTaskId(), getQueueSize());
                throw new PrintQueueFullException("打印队列已满，请稍后重试");
            }
            log.info("成功添加打印任务到队列: {}", task.getTaskId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PrintTaskException("添加打印任务被中断", e);
        }
    }

    // 定时处理打印任务
    @Scheduled(fixedRate = 1000)
    public void processPrintTasks() {
        // 从队列中取出任务
        PrintTask task = printQueue.poll();
        if (task != null) {
            taskExecutor.execute(() -> {
                try {
                    task.setStatus(PrintTaskStatus.PRINTING);
                    printService.executePrint(task);
                    task.setStatus(PrintTaskStatus.COMPLETED);
                    log.info("打印任务完成: {}", task.getTaskId());
                } catch (Exception e) {
                    handleFailedTask(task);
                }
            });

        }
    }

    // 处理失败任务
    private void handleFailedTask(PrintTask task) {
        task.setStatus(PrintTaskStatus.FAILED);
        task.setRetryCount(task.getRetryCount() + 1);

        if (task.getRetryCount() < maxRetry) {

            try{
                // 使用put方法确保任务一定能重新入队
                printQueue.put(task);
                log.info("打印任务重新入队: {}, 重试次数: {}", task.getTaskId(), task.getRetryCount());
                // 添加延迟重试机制
                long waitTime = (long) (Math.pow(2, task.getRetryCount()) * 1000L);
                long jitter = new Random().nextInt(1000);// 随机延迟时间
                Thread.sleep(waitTime + jitter); // 重试间隔逐渐增加
            }catch (InterruptedException e){

                // 重新入队失败
                Thread.currentThread().interrupt();
                log.error("打印任务重新入队失败: {}", task.getTaskId(), e);
            }
        } else {
            log.error("打印任务达到最大重试次数: {}", task.getTaskId());
        }
    }


    // 添加自定义异常
    public static class PrintQueueFullException extends RuntimeException {
        public PrintQueueFullException(String message) {
            super(message);
        }
    }

    // 添加自定义异常
    public static class PrintTaskException extends RuntimeException {
        public PrintTaskException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // 添加获取队列大小的方法，便于监控
    public int getQueueSize() {
        return printQueue.size();
    }
}
