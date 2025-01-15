package org.example.print;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.print.PrintException;

import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskPriority;
import org.example.print.bean.PrintTaskStatus;
import org.example.print.component.PrintQueue;
import org.example.print.component.PrintQueueManager;
import org.example.print.service.UnifiedPrintService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootTest
@EnableScheduling  // 添加此注解以启用定时任务
public class PrintServiceTest {

    @Autowired
    private PrintQueueManager printQueueManager;

    @Autowired
    private PrintQueue printQueue;

    @MockBean  // 使用Mock替换真实的打印服务，避免实际打印操作
    private UnifiedPrintService enhancedPrintService;

    @Test
    @DisplayName("测试打印任务的基本流程")
    public void testPrintTaskFlow() throws PrintException {
        // 1. 准备测试数据
        PrintTask task = PrintTask.builder()
                .taskId(UUID.randomUUID().toString())
                .content("测试打印内容")
                .printerName("默认打印机")
                .status(PrintTaskStatus.PENDING)
                .retryCount(0)
                .createTime(LocalDateTime.now())
                .build();

        // 2. 设置Mock行为
        doNothing().when(enhancedPrintService).executePrint(any(PrintTask.class));

        // 3. 执行测试
        printQueueManager.addPrintTask(task);

        // 4. 等待任务处理
        printQueueManager.processPrintTasks();

        // 5. 验证结果
        verify(enhancedPrintService, times(1)).executePrint(any(PrintTask.class));
        assertTrue(printQueue.isEmpty(), "打印队列应该为空");
    }

    @Test
    @DisplayName("测试打印失败重试机制")
    public void testPrintRetryMechanism() throws PrintException {
        // 1. 准备测试数据
        PrintTask task = PrintTask.builder()
                .taskId(UUID.randomUUID().toString())
                .content("测试重试打印内容")
                .printerName("默认打印机")
                .status(PrintTaskStatus.PENDING)
                .retryCount(0)
                .createTime(LocalDateTime.now())
                .build();

        // 2. 设置Mock行为 - 第一次失败，第二次成功
        doThrow(new PrintException("打印失败"))
                .doNothing()
                .when(enhancedPrintService).executePrint(any(PrintTask.class));

        // 3. 执行测试
        printQueueManager.addPrintTask(task);

        // 4. 等待重试完成
        try {
            Thread.sleep(3000); // 等待足够的时间让重试发生
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 5. 验证结果
        verify(enhancedPrintService, times(2)).executePrint(any(PrintTask.class));
        assertTrue(printQueue.isEmpty(), "打印队列应该为空");
    }

    @Test
    @DisplayName("测试队列容量")
    public void testQueueCapacity() {
        // 1. 准备多个测试任务
        List<PrintTask> tasks = IntStream.range(0, 5)
                .mapToObj(i -> PrintTask.builder()
                        .taskId(UUID.randomUUID().toString())
                        .content("测试内容 " + i)
                        .printerName("默认打印机")
                        .status(PrintTaskStatus.PENDING)
                        .retryCount(0)
                        .createTime(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        // 2. 将任务添加到队列
        //tasks.forEach(task -> {
        //    boolean added = printQueue.offer(task, 1, TimeUnit.SECONDS,);
        //    assertTrue(added, "应该能够添加任务到队列");
        //});

        // 3. 验证队列状态
        assertFalse(printQueue.isEmpty(), "队列不应该为空");
    }


    @Test
    @DisplayName("测试并发打印请求处理")
    @Timeout(value = 10)
    public void testConcurrentPrinting() throws InterruptedException {
        // 配置
        int numberOfTasks = 50;
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfTasks);
        List<Exception> exceptions = new ArrayList<>();

        // 模拟多线程并发打印
        IntStream.range(0, numberOfTasks).forEach(i -> {
            executorService.submit(() -> {
                try {
                    PrintTask task = PrintTask.builder()
                            .taskId(UUID.randomUUID().toString())
                            .content("并发测试打印内容 " + i)
                            .printerName("默认打印机")
                            .status(PrintTaskStatus.PENDING)
                            .retryCount(0)
                            .createTime(LocalDateTime.now())
                            .build();
                    printQueueManager.addPrintTask(task);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        });

        // 等待所有任务完成或超时
        assertTrue(latch.await(5, TimeUnit.SECONDS), "并发测试超时");
        executorService.shutdown();
        assertTrue(exceptions.isEmpty(), "并发处理过程中出现异常");
    }

    @Test
    @DisplayName("测试打印机离线异常处理")
    public void testPrinterOfflineHandling() throws PrintException {
        // 模拟打印机离线情况
        PrintTask task = PrintTask.builder()
                .taskId(UUID.randomUUID().toString())
                .content("测试打印机离线内容")
                .printerName("离线打印机")
                .status(PrintTaskStatus.PENDING)
                .retryCount(0)
                .createTime(LocalDateTime.now())
                .build();

        // 模拟打印机离线抛出异常
        doThrow(new PrintException("打印机离线"))
                .when(enhancedPrintService)
                .executePrint(any(PrintTask.class));

        // 执行测试
        printQueueManager.addPrintTask(task);

        // 使用 Awaitility 等待重试机制执行
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(enhancedPrintService, atLeast(3)).executePrint(any(PrintTask.class))
                );

        // 验证任务最终状态
        assertEquals(PrintTaskStatus.FAILED, task.getStatus());
        assertTrue(task.getRetryCount() >= 3, "应该至少重试3次");
    }

    @Test
    @DisplayName("测试打印格式异常处理")
    public void testPrintFormatException() throws PrintException {
        // 准备包含特殊字符的打印内容
        PrintTask task = PrintTask.builder()
                .taskId(UUID.randomUUID().toString())
                .content("包含特殊字符的内容\u0000\u001F")
                .printerName("默认打印机")
                .status(PrintTaskStatus.PENDING)
                .retryCount(0)
                .createTime(LocalDateTime.now())
                .build();

        // 模拟格式异常
        doThrow(new PrintException("不支持的字符格式"))
                .when(enhancedPrintService)
                .executePrint(any(PrintTask.class));

        // 执行测试
        printQueueManager.addPrintTask(task);

        // 手动触发处理任务
        printQueueManager.processPrintTasks();

        // 使用 Awaitility 等待处理完成
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    verify(enhancedPrintService, times(1)).executePrint(any(PrintTask.class));
                    assertEquals(PrintTaskStatus.FAILED, task.getStatus());
                });
    }

    @Test
    @DisplayName("测试打印任务优先级处理")
    public void testPrintTaskPriority() {
        // 创建不同优先级的打印任务
        PrintTask highPriorityTask = PrintTask.builder()
                .taskId(UUID.randomUUID().toString())
                .content("高优先级打印内容")
                .printerName("默认打印机")
                .priority(PrintTaskPriority.HIGH)
                .status(PrintTaskStatus.PENDING)
                .createTime(LocalDateTime.now())
                .build();

        PrintTask lowPriorityTask = PrintTask.builder()
                .taskId(UUID.randomUUID().toString())
                .content("低优先级打印内容")
                .printerName("默认打印机")
                .priority(PrintTaskPriority.LOW)
                .status(PrintTaskStatus.PENDING)
                .createTime(LocalDateTime.now())
                .build();

        // 先添加低优先级任务，再添加高优先级任务
        printQueueManager.addPrintTask(lowPriorityTask);
        printQueueManager.addPrintTask(highPriorityTask);


        // 等待队列处理
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    PrintTask processedTask = printQueue.poll();
                    assertNotNull(processedTask);
                    assertEquals(PrintTaskPriority.HIGH, processedTask.getPriority(),
                            "应该先处理高优先级任务");
                });

    }



}
