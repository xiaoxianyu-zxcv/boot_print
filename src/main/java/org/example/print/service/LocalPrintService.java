package org.example.print.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.print.*;
import javax.print.attribute.standard.PrinterState;
import javax.print.attribute.standard.PrinterStateReason;
import javax.print.attribute.standard.PrinterStateReasons;
import javax.print.attribute.standard.Severity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;




@Service
@Slf4j
public class LocalPrintService  {

    public void print(JSONObject printData) {

        if (!isPrinterReady()) {
            throw new PrinterNotAvailableException("打印机未就绪");
        }

        try {
            // 获取默认打印机
            javax.print.PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
            if (defaultPrinter == null) {
                throw new PrinterNotAvailableException("打印机未就绪，请检查打印机状态");
            }

            // 创建打印任务
            DocPrintJob job = defaultPrinter.createPrintJob();

            // 创建打印内容
            String content = formatPrintContent(printData);
            Doc doc = new SimpleDoc(content.getBytes("GBK"), DocFlavor.BYTE_ARRAY.AUTOSENSE, null);

            // 执行打印
            job.print(doc, null);

            log.info("打印任务已发送到打印机");

        } catch (Exception e) {
            log.error("打印失败", e);
            throw new PrintException("打印失败: " + e.getMessage());
        }
    }

    private String formatPrintContent(JSONObject data) {
        StringBuilder content = new StringBuilder();
        content.append("配送单\n");
        content.append("--------------------------------\n");
        content.append("订单号: ").append(data.getString("orderNo")).append("\n");
        content.append("下单时间: ").append(data.getString("orderTime")).append("\n");
        content.append("商家名称: ").append(data.getString("merchant")).append("\n");
        content.append("商品信息: ").append(data.getString("goods")).append("\n");
        content.append("顾客信息: ").append(data.getString("customer"))
                .append(" ").append(data.getString("customerPhone")).append("\n");
        content.append("收货地址: ").append(data.getString("address")).append("\n");
        content.append("配送费: ").append(data.getString("deliveryFee")).append("\n");
        content.append("商品总价: ￥").append(data.getString("totalPrice")).append("\n");
        content.append("实付金额: ￥").append(data.getString("actualPayment")).append("\n");
        content.append("支付方式: ").append(data.getString("paymentMethod")).append("\n");
        content.append("--------------------------------\n");
        content.append("打印时间: ").append(getCurrentTime()).append("\n\n\n");  // 多加几个换行，方便撕单

        return content.toString();
    }

    // 获取当前时间
    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }


    // 添加打印机状态检测
    public boolean isPrinterReady() {
        try {
            PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
            if (defaultPrinter == null) {
                log.error("未找到默认打印机");
                return false;
            }


            // 获取打印机属性集
            PrinterState printerState = (PrinterState) defaultPrinter.getAttribute(PrinterState.class);
            PrinterStateReasons stateReasons = (PrinterStateReasons) defaultPrinter.getAttribute(PrinterStateReasons.class);


            // 如果打印机状态为null，假定打印机可用（某些打印机可能不报告状态）
            if (printerState == null) {
                log.warn("无法获取打印机状态，假定打印机可用");
                return true;
            }

            // 检查打印机状态原因
            if (stateReasons != null && !stateReasons.isEmpty()) {
                for (PrinterStateReason reason : stateReasons.keySet()) {
                    Severity severity = stateReasons.get(reason);
                    if (severity == Severity.ERROR) {
                        log.error("打印机错误: {}", reason.toString());
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

    // 添加自定义异常类
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public static class PrinterNotAvailableException extends RuntimeException {
        public PrinterNotAvailableException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public static class PrintException extends RuntimeException {
        public PrintException(String message) {
            super(message);
        }
    }


}
