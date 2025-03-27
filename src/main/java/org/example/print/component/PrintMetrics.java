package org.example.print.component;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 打印任务统计
 */
@Component
public class PrintMetrics {
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong successTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);

    public void recordSuccess() {
        totalTasks.incrementAndGet();
        successTasks.incrementAndGet();
    }

    public void recordFailure() {
        totalTasks.incrementAndGet();
        failedTasks.incrementAndGet();
    }

    public double getSuccessRate() {
        long total = totalTasks.get();
        return total == 0 ? 0 : (double) successTasks.get() / total;
    }
}
