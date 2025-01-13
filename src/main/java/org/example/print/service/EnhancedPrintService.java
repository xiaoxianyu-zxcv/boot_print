package org.example.print.service;

import lombok.extern.slf4j.Slf4j;
import org.example.print.bean.PrintTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.print.*;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class EnhancedPrintService {

    @Value("${print.max-retry:3}")
    private int maxRetry;

    // 获取所有打印机
    public List<PrintService> getAllPrinters() {
        return Arrays.asList(PrintServiceLookup.lookupPrintServices(null, null));
    }

    // 根据名称获取打印机
    public PrintService getPrinterByName(String printerName) {
        return getAllPrinters().stream()
                .filter(printer -> printer.getName().equals(printerName))
                .findFirst()
                .orElse(null);
    }

    // 执行打印
    public void executePrint(PrintTask task) throws PrintException {
        PrintService printService = getPrinterByName(task.getPrinterName());
        if (printService == null) {
            throw new PrintException("打印机未找到: " + task.getPrinterName());
        }

        try {
            DocPrintJob job = printService.createPrintJob();
            byte[] content = task.getContent().getBytes("GBK");
            Doc doc = new SimpleDoc(content, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
            job.print(doc, null);
            log.info("打印任务执行成功: {}", task.getTaskId());
        } catch (Exception e) {
            log.error("打印任务执行失败: {}", task.getTaskId(), e);
            throw new PrintException("打印执行失败: " + e.getMessage());
        }
    }
}
