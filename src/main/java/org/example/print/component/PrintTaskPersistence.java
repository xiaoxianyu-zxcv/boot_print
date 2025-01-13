package org.example.print.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.print.bean.PrintTask;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class PrintTaskPersistence {
    private static final String TASK_DIR = "print_tasks";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void savePendingTask(PrintTask task) {
        File dir = new File(TASK_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = String.format("%s_%s.json",
                task.getTaskId(),
                task.getCreateTime().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        );

        try {
            File file = new File(dir, fileName);
            objectMapper.writeValue(file, task);
            log.info("任务持久化成功: {}", fileName);
        } catch (IOException e) {
            log.error("任务持久化失败: {}", task.getTaskId(), e);
        }
    }

    public List<PrintTask> loadPendingTasks() {
        List<PrintTask> tasks = new ArrayList<>();
        File dir = new File(TASK_DIR);

        if (!dir.exists()) {
            return tasks;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return tasks;
        }

        for (File file : files) {
            try {
                PrintTask task = objectMapper.readValue(file, PrintTask.class);
                tasks.add(task);
                file.delete(); // 加载后删除文件
            } catch (IOException e) {
                log.error("加载任务失败: {}", file.getName(), e);
            }
        }

        return tasks;
    }
}