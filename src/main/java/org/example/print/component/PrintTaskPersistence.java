package org.example.print.component;

import org.example.print.bean.PrintTask;
import org.springframework.stereotype.Component;

import java.util.List;

//@Component
public class PrintTaskPersistence {
    private static final String TASK_DIR = "print_tasks";

    public void savePendingTask(PrintTask task) {
        // 将任务保存到本地文件
    }

    public List<PrintTask> loadPendingTasks() {
        // 加载未完成的任务
        return null;
    }
}