package org.example.print;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.print.PrintException;

import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.example.print.component.PrintQueue;
import org.example.print.component.PrintQueueManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
public class PrintServiceTest {

    @Autowired
    private PrintQueueManager printQueueManager;

    @Autowired
    private PrintQueue printQueue;

    @MockBean  // 使用Mock替换真实的打印服务，避免实际打印操作
    private EnhancedPrintService enhancedPrintService;

    @Test
    @DisplayName("测试打印任务的基本流程")
    public void testPrintTaskFlow() {
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
        try {
            Thread.sleep(2000); // 等待定时任务执行
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 5. 验证结果
        verify(enhancedPrintService, times(1)).executePrint(any(PrintTask.class));
        assertTrue(printQueue.isEmpty(), "打印队列应该为空");
    }

    @Test
    @DisplayName("测试打印失败重试机制")
    public void testPrintRetryMechanism() {
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
        tasks.forEach(task -> {
            boolean added = printQueue.offer(task);
            assertTrue(added, "应该能够添加任务到队列");
        });

        // 3. 验证队列状态
        assertFalse(printQueue.isEmpty(), "队列不应该为空");
    }
}
