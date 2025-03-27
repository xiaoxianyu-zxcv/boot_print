package org.example.print.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.example.print.component.PrintMetrics;
import org.example.print.component.PrintQueueManager;
import org.example.print.component.PrintTaskPersistence;
import org.example.print.service.UnifiedPrintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.print.PrintService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 打印API控制器
 * 提供RESTful API接口供前端调用
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class PrintApiController {

    private final PrintQueueManager printQueueManager;
    private final PrintTaskPersistence printTaskPersistence;
    private final UnifiedPrintService printService;
    private final PrintMetrics printMetrics;

    @Autowired
    public PrintApiController(
            PrintQueueManager printQueueManager,
            PrintTaskPersistence printTaskPersistence,
            UnifiedPrintService printService,
            PrintMetrics printMetrics) {
        this.printQueueManager = printQueueManager;
        this.printTaskPersistence = printTaskPersistence;
        this.printService = printService;
        this.printMetrics = printMetrics;
    }

    /**
     * 获取所有待处理的打印任务
     */
    @GetMapping("/tasks/pending")
    public ResponseEntity<List<PrintTask>> getPendingTasks() {
        List<PrintTask> tasks = printTaskPersistence.loadPendingTasks();
        return ResponseEntity.ok(tasks);
    }

    /**
     * 获取打印队列状态
     */
    @GetMapping("/queue/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("queueSize", printQueueManager.getQueueSize());
        status.put("successRate", printMetrics.getSuccessRate());
        status.put("printerReady", printService.isPrinterReady(null));
        return ResponseEntity.ok(status);
    }

    /**
     * 获取可用打印机列表
     */
    @GetMapping("/printers")
    public ResponseEntity<List<Map<String, String>>> getPrinters() {
        List<PrintService> printers = printService.getAllPrinters();

        List<Map<String, String>> printerList = printers.stream()
                .map(printer -> {
                    Map<String, String> printerInfo = new HashMap<>();
                    printerInfo.put("name", printer.getName());
                    return printerInfo;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(printerList);
    }

    /**
     * 添加打印任务
     */
    @PostMapping("/tasks")
    public ResponseEntity<PrintTask> addPrintTask(@RequestBody PrintTask task) {
        try {
            log.info("收到打印请求: {}", task);
            printQueueManager.addPrintTask(task);
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            log.error("添加打印任务失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 取消打印任务
     */
    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<?> cancelTask(@PathVariable String taskId) {
        // 这里需要实现取消功能
        // 实际应用中可能需要在PrintQueueManager中添加取消方法
        log.info("请求取消任务: {}", taskId);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取系统状态
     */
    @GetMapping("/system/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("version", "1.0.0");
        status.put("queueSize", printQueueManager.getQueueSize());
        status.put("successRate", printMetrics.getSuccessRate());
        status.put("printerReady", printService.isPrinterReady(null));

        // 获取当前默认打印机
        PrintService printer = printService.getPrinterByName(null);
        status.put("currentPrinter", printer != null ? printer.getName() : "未设置");

        return ResponseEntity.ok(status);
    }

    /**
     * 测试打印接口
     */
    @PostMapping("/print/test")
    public ResponseEntity<?> testPrint(@RequestBody(required = false) Map<String, String> params) {
        try {
            String content = params != null && params.containsKey("content")
                    ? params.get("content")
                    : "测试打印内容\n这是一条测试消息\n打印时间: " + java.time.LocalDateTime.now();

            PrintTask task = PrintTask.builder()
                    .taskId(java.util.UUID.randomUUID().toString())
                    .content(content)
                    .status(PrintTaskStatus.PENDING)
                    .createTime(java.time.LocalDateTime.now())
                    .retryCount(0)
                    .printerName(null) // 使用默认打印机
                    .build();

            printQueueManager.addPrintTask(task);


            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("success", true);
            responseMap.put("message", "测试打印任务已添加");
            responseMap.put("taskId", task.getTaskId());
            return ResponseEntity.ok().body(responseMap);
        } catch (Exception e) {
            log.error("测试打印失败", e);
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("success", false);
            responseMap.put("message", "测试打印失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(responseMap);
        }
    }

    /**
     * 设置默认打印机
     */
    @PostMapping("/settings/printer")
    public ResponseEntity<?> setDefaultPrinter(@RequestBody Map<String, String> params) {
        try {
            String printerName = params.get("printerName");
            if (printerName == null || printerName.isEmpty()) {
                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("success", false);
                responseMap.put("message", "打印机名称不能为空");
                return ResponseEntity.badRequest().body(responseMap);
            }

            // 这里需要实现保存打印机设置的功能
            // 示例中只打印日志
            log.info("设置默认打印机: {}", printerName);

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("success", true);
            responseMap.put("message", "默认打印机已更新");
            return ResponseEntity.ok().body(responseMap);
        } catch (Exception e) {
            log.error("设置默认打印机失败", e);
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("success", false);
            responseMap.put("message", "设置默认打印机失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(responseMap);
        }
    }
}