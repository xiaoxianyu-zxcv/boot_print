package org.example.print.component;

import lombok.extern.slf4j.Slf4j;
import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.example.print.service.PrintTaskNotificationService;
import org.example.print.service.UnifiedPrintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 打印队列管理器
 */
@Component
@Slf4j
public class PrintQueueManager {

    private final PrintQueue printQueue;
    private final UnifiedPrintService printService;
    private final Executor taskExecutor;
    private final PrintTaskPersistence printTaskPersistence;
    private final PrintMetrics printMetrics;
    private final PrintTaskNotificationService notificationService;

    @Value("${print.max-retry:3}")
    private int maxRetry;

    @Value("${print.queue.offer-timeout:3}")
    private int offerTimeout;

    @Autowired
    public PrintQueueManager(
            PrintQueue printQueue,
            UnifiedPrintService printService,
            @Qualifier("printTaskExecutor") Executor taskExecutor,
            PrintTaskPersistence printTaskPersistence,
            PrintMetrics printMetrics,
            PrintTaskNotificationService notificationService) {
        this.printQueue = printQueue;
        this.printService = printService;
        this.taskExecutor = taskExecutor;
        this.printTaskPersistence = printTaskPersistence;
        this.printMetrics = printMetrics;
        this.notificationService = notificationService;
    }

    // 添加打印任务
    public void addPrintTask(PrintTask task) {
        task.setStatus(PrintTaskStatus.PENDING);
        if (task.getCreateTime() == null) {
            task.setCreateTime(LocalDateTime.now());
        }

        try {
            // 先持久化任务
            printTaskPersistence.savePendingTask(task);

            // 使用带超时的offer，给一个短暂的等待时间
            boolean added = printQueue.offer(task, offerTimeout, TimeUnit.SECONDS);
            if (!added) {
                log.error("队列已满，无法添加任务: {}, 当前队列大小: {}",
                        task.getTaskId(), getQueueSize());

                task.setStatus(PrintTaskStatus.FAILED);

                // 通知客户端任务添加失败
                notificationService.notifyClient(task);

                // 通知远程服务器任务添加失败
                notificationService.notifyRemoteServer(task.getTaskId(), PrintTaskStatus.FAILED);

                throw new PrintQueueFullException("打印队列已满，请稍后重试");
            }
            log.info("成功添加打印任务到队列: {}", task.getTaskId());

            // 通知客户端和远程服务器任务状态
            notificationService.notifyAll(task);

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

                    // 通知客户端和远程服务器任务状态
                    notificationService.notifyAll(task);

                    // 使用CompletableFuture异步处理打印结果
                    CompletableFuture<UnifiedPrintService.PrintResult> future =
                            printService.executePrint(task);

                    future.thenAccept(result -> {
                        if (result.isSuccess()) {
                            task.setStatus(PrintTaskStatus.COMPLETED);
                            log.info("打印任务完成: {}", task.getTaskId());

                            // 通知客户端和远程服务器任务状态
                            notificationService.notifyAll(task);

                        } else {
                            handleFailedTask(task);
                        }
                        // 更新持久化状态
                        printTaskPersistence.savePendingTask(task);
                    });
                } catch (Exception e) {
                    handlePrintResult(task, false);
                    handleFailedTask(task);
                }
            });
        }
    }

    // 处理失败任务
    private void handleFailedTask(PrintTask task) {
        task.setStatus(PrintTaskStatus.FAILED);
        task.setRetryCount(task.getRetryCount() + 1);

        // 通知客户端和远程服务器任务状态
        notificationService.notifyAll(task);

        if (task.getRetryCount() < maxRetry) {
            try {
                // 使用put方法确保任务一定能重新入队
                printQueue.put(task);
                log.info("打印任务重新入队: {}, 重试次数: {}", task.getTaskId(), task.getRetryCount());

                // 添加延迟重试机制
                long waitTime = (long) (Math.pow(2, task.getRetryCount()) * 1000L);
                long jitter = new Random().nextInt(1000);// 随机延迟时间
                Thread.sleep(waitTime + jitter); // 重试间隔逐渐增加
            } catch (InterruptedException e) {
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

    // 在系统启动时加载未完成的任务
    @PostConstruct
    public void init() {
        List<PrintTask> pendingTasks = printTaskPersistence.loadPendingTasks();
        pendingTasks.forEach(task -> {
            try {
                printQueue.put(task);
                log.info("成功加载持久化任务: {}", task.getTaskId());
            } catch (InterruptedException e) {
                log.error("加载持久化任务失败: {}", task.getTaskId(), e);
                Thread.currentThread().interrupt();
            }
        });
    }

    private void handlePrintResult(PrintTask task, boolean success) {
        if (success) {
            printMetrics.recordSuccess();
        } else {
            printMetrics.recordFailure();
        }
    }
}
