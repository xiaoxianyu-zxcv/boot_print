package org.example.print.service;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.example.print.component.PrintMetrics;
import org.example.print.component.PrintTaskPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.print.*;
import javax.print.attribute.standard.PrinterState;
import javax.print.attribute.standard.PrinterStateReason;
import javax.print.attribute.standard.PrinterStateReasons;
import javax.print.attribute.standard.Severity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class UnifiedPrintService {

    @Value("${print.max-retry:3}")
    private int maxRetry;

    @Autowired
    private PrintMetrics printMetrics;

    @Autowired
    private PrintTaskPersistence printTaskPersistence;

    // 获取所有打印机
    public List<PrintService> getAllPrinters() {
        return Arrays.asList(PrintServiceLookup.lookupPrintServices(null, null));
    }

    // 根据名称获取打印机
    public PrintService getPrinterByName(String printerName) {
        if (printerName == null || printerName.trim().isEmpty()) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
        return getAllPrinters().stream()
                .filter(printer -> printer.getName().equals(printerName))
                .findFirst()
                .orElse(PrintServiceLookup.lookupDefaultPrintService());
    }

    // 执行打印任务
    public CompletableFuture<PrintResult> executePrint(PrintTask task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isPrinterReady(task.getPrinterName())) {
                    throw new PrinterNotAvailableException("打印机未就绪: " + task.getPrinterName());
                }

                PrintService printService = getPrinterByName(task.getPrinterName());
                if (printService == null) {
                    throw new PrinterNotAvailableException("找不到可用的打印机");
                }

                // 解析任务内容
                JSONObject printData = JSONObject.parseObject(task.getContent());
                String formattedContent = formatPrintContent(printData);

                // 创建打印作业
                DocPrintJob job = printService.createPrintJob();
                Doc doc = new SimpleDoc(formattedContent.getBytes("GBK"),
                        DocFlavor.BYTE_ARRAY.AUTOSENSE,
                        null);

                // 执行打印
                job.print(doc, null);

                // 更新任务状态
                task.setStatus(PrintTaskStatus.COMPLETED);
                printTaskPersistence.markTaskAsCompleted(task);
                printMetrics.recordSuccess();

                return new PrintResult(true, "打印成功");
            } catch (Exception e) {
                task.setStatus(PrintTaskStatus.FAILED);
                printMetrics.recordFailure();
                log.error("打印失败: {}", task.getTaskId(), e);
                return new PrintResult(false, "打印失败: " + e.getMessage());
            }
        });
    }

    // 检查打印机状态
    public boolean isPrinterReady(String printerName) {
        try {
            PrintService printer = getPrinterByName(printerName);
            if (printer == null) {
                log.error("未找到打印机: {}", printerName);
                return false;
            }

            PrinterState printerState = (PrinterState) printer.getAttribute(PrinterState.class);
            PrinterStateReasons stateReasons = (PrinterStateReasons) printer.getAttribute(PrinterStateReasons.class);

            if (printerState == null) {
                log.warn("无法获取打印机状态，假定打印机可用");
                return true;
            }

            if (stateReasons != null && !stateReasons.isEmpty()) {
                for (PrinterStateReason reason : stateReasons.keySet()) {
                    if (stateReasons.get(reason) == Severity.ERROR) {
                        log.error("打印机错误: {}", reason);
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            log.error("检查打印机状态时发生错误", e);
            return false;
        }
    }

    // 格式化打印内容
    private String formatPrintContent(JSONObject data) {
        StringBuilder content = new StringBuilder();


        // ESC/POS 指令常量
        final String ESC = "\u001B";
        final String GS = "\u001D";
        // 字体放大指令: ESC ! n  (n = 0-255, 位0-3表示字体，位4-7表示大小)
        final String NORMAL_SIZE = ESC + "!0";  // 正常大小
        final String LARGE_SIZE = ESC + "!16";  // 双倍大小


        // 标题部分
        content.append("         配送单\n");
        content.append(LARGE_SIZE)  // 切换到大号字体
                .append("指尖赤壁\n")
                .append("========\n")
                .append(data.getString("merchant")).append("\n")  // 商家名称也使用大号字体
                .append(NORMAL_SIZE);  // 切换回正常字体

        content.append("#").append(data.getString("day_index")).append("\n\n");

        // 订单信息部分
        content.append("订单号: ").append(data.getString("orderNo")).append("\n");
        content.append("下单时间: ").append(data.getString("orderTime").substring(5, 16)).append("\n");
        String goodsStr = data.getString("goods");
        String[] goods = goodsStr.split(", ");  // 按逗号分割商品
        for (String good : goods) {
            content.append("  ").append(good).append("\n");  // 缩进显示每个商品
        }
        content.append("配送费: ").append(data.getString("deliveryFee")).append("\n");
        content.append("商品总价: ￥").append(data.getString("totalPrice")).append("\n");
        content.append("实付金额: ￥").append(data.getString("actualPayment")).append("\n");
        content.append("支付方式: ").append(data.getString("paymentMethod")).append("\n");
        content.append("配送状态: ").append(data.getString("delivery_status")).append("\n");

        // 第一条分隔线
        content.append("-----------------------------\n");

        // 顾客信息部分
        content.append("顾客信息: ")
                .append(data.getString("customer"))
                .append(" ")
                .append(data.getString("customerPhone"))
                .append("\n");
        content.append("收货地址: ").append(data.getString("address")).append("\n");

        // 打印时间和结束分隔线
        content.append("打印时间: ").append(getCurrentTime()).append("\n");
        content.append("-----------------------------\n\n\n");  // 留出撕纸空间

        return content.toString();
    }

    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // 打印结果类
    public static class PrintResult {
        private final boolean success;
        private final String message;

        public PrintResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    // 自定义异常
    public static class PrinterNotAvailableException extends RuntimeException {
        public PrinterNotAvailableException(String message) {
            super(message);
        }
    }
}
