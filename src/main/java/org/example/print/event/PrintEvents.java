package org.example.print.event;

import org.example.print.bean.PrintTask;
import org.example.print.bean.PrintTaskStatus;
import org.springframework.context.ApplicationEvent;

/**
 * 打印系统事件类
 * 用于解耦组件间的直接依赖关系
 */
public class PrintEvents {

    /**
     * 打印任务状态变更事件
     * 当打印任务状态发生变化时发布此事件
     */
    public static class TaskStatusChangeEvent extends ApplicationEvent {
        private final PrintTaskStatus status;

        public TaskStatusChangeEvent(PrintTask task, PrintTaskStatus status) {
            super(task);
            this.status = status;
        }

        public PrintTask getTask() {
            return (PrintTask) getSource();
        }

        public PrintTaskStatus getStatus() {
            return status;
        }
    }

    /**
     * 打印任务添加事件
     * 当新的打印任务被添加到队列时发布此事件
     */
    public static class TaskAddedEvent extends ApplicationEvent {
        public TaskAddedEvent(PrintTask task) {
            super(task);
        }

        public PrintTask getTask() {
            return (PrintTask) getSource();
        }
    }

    /**
     * 打印任务完成事件
     * 当打印任务成功完成时发布此事件
     */
    public static class TaskCompletedEvent extends ApplicationEvent {
        public TaskCompletedEvent(PrintTask task) {
            super(task);
        }

        public PrintTask getTask() {
            return (PrintTask) getSource();
        }
    }

    /**
     * 打印任务失败事件
     * 当打印任务执行失败时发布此事件
     */
    public static class TaskFailedEvent extends ApplicationEvent {
        private final String errorMessage;

        public TaskFailedEvent(PrintTask task, String errorMessage) {
            super(task);
            this.errorMessage = errorMessage;
        }

        public PrintTask getTask() {
            return (PrintTask) getSource();
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}