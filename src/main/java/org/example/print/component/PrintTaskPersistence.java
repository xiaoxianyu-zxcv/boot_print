package org.example.print.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class PrintTaskPersistence {
    private static final String TASK_DIR = "print_tasks";
    private static final String COMPLETED_DIR = "completed_tasks";
    private final ObjectMapper objectMapper;

    @Autowired  // 注入全局配置的 ObjectMapper
    public PrintTaskPersistence(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        initDirectories();
    }


    private void initDirectories() {
        new File(TASK_DIR).mkdirs();
        new File(COMPLETED_DIR).mkdirs();
    }


    // 保存任务
    public void savePendingTask(PrintTask task) {
        String fileName = generateFileName(task);
        saveTaskToFile(new File(TASK_DIR, fileName), task);
    }


    /**
     * 加载待处理任务
     * 该方法会从待处理目录中读取所有任务，并进行状态验证
     */
    public List<PrintTask> loadPendingTasks() {
        List<PrintTask> tasks = new ArrayList<>();
        File pendingDir = new File(TASK_DIR);

        if (!pendingDir.exists()) {
            log.info("待处理任务目录不存在，跳过加载");
            return tasks;
        }

        File[] files = pendingDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            log.warn("无法读取待处理任务目录");
            return tasks;
        }

        for (File file : files) {
            try {
                PrintTask task = objectMapper.readValue(file, PrintTask.class);

                // 验证任务状态
                if (task.getStatus() == PrintTaskStatus.COMPLETED) {
                    // 如果发现已完成的任务，将其移动到已完成目录
                    moveToCompletedDirectory(file, task);
                    continue;
                }

                // 检查任务是否过期（例如24小时未处理的任务）
                if (isTaskExpired(task)) {
                    log.warn("任务已过期: {}", task.getTaskId());
                    moveToCompletedDirectory(file, task);
                    continue;
                }

                tasks.add(task);
                log.info("成功加载待处理任务: {}", task.getTaskId());

            } catch (IOException e) {
                log.error("加载任务失败: {}", file.getName(), e);
                // 对于损坏的文件，可以选择移动到一个特殊的错误目录中
                moveToErrorDirectory(file);
            }
        }

        return tasks;
    }

    /**
     * 检查任务是否过期
     */
    private boolean isTaskExpired(PrintTask task) {
        LocalDateTime expirationTime = task.getCreateTime().plusHours(24);
        return LocalDateTime.now().isAfter(expirationTime);
    }

    /**
     * 将文件移动到已完成目录
     */
    private void moveToCompletedDirectory(File file, PrintTask task) {
        try {
            File targetFile = new File(COMPLETED_DIR, file.getName());
            Files.move(file.toPath(), targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            log.info("任务已移动到已完成目录: {}", task.getTaskId());
        } catch (IOException e) {
            log.error("移动已完成任务失败: {}", task.getTaskId(), e);
        }
    }

    /**
     * 将损坏的文件移动到错误目录
     */
    private void moveToErrorDirectory(File file) {
        try {
            File errorDir = new File("error_tasks");
            if (!errorDir.exists()) {
                errorDir.mkdirs();
            }

            File targetFile = new File(errorDir, file.getName());
            Files.move(file.toPath(), targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            log.info("损坏的任务文件已移动到错误目录: {}", file.getName());
        } catch (IOException e) {
            log.error("移动损坏任务文件失败: {}", file.getName(), e);
        }
    }


    // 标记任务为已完成
    public void markTaskAsCompleted(PrintTask task) {
        String fileName = generateFileName(task);
        File sourceFile = new File(TASK_DIR, fileName);
        File targetFile = new File(COMPLETED_DIR, fileName);

        try {
            if (sourceFile.exists()) {
                Files.move(sourceFile.toPath(), targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                log.info("任务已标记为完成: {}", task.getTaskId());
            }
        } catch (IOException e) {
            log.error("标记任务完成失败: {}", task.getTaskId(), e);
        }
    }


    // 清理已完成的任务
    @Scheduled(cron = "0 0 0 * * ?")  // 每天零点执行
    public void cleanupCompletedTasks() {
        File completedDir = new File(COMPLETED_DIR);
        if (completedDir.exists()) {
            File[] files = completedDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.lastModified() < System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000) {
                        if (file.delete()) {
                            log.info("清理已完成任务: {}", file.getName());
                        }
                    }
                }
            }
        }
    }


    // 生成文件名
    private String generateFileName(PrintTask task) {
        return String.format("%s_%s.json",
                task.getTaskId(),
                task.getCreateTime().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
    }

    // 保存任务到文件
    private void saveTaskToFile(File file, PrintTask task) {
        try {
            objectMapper.writeValue(file, task);
            log.info("任务持久化成功: {}", file.getName());
        } catch (IOException e) {
            log.error("任务持久化失败: {}", task.getTaskId(), e);
        }
    }


}