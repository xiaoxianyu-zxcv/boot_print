package org.example.print.component;


import org.example.print.bean.PrintTask;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 打印队列
 */
@Component
public class PrintQueue {
    private final BlockingQueue<PrintTask> queue = new LinkedBlockingQueue<>(
            1000
    );

    public boolean offer(PrintTask task, long timeout, TimeUnit unit) throws InterruptedException {
        return queue.offer(task, timeout, unit);
    }

    public PrintTask poll() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void put(PrintTask task) throws InterruptedException {
        queue.put(task);
    }

    public int size() {
        return queue.size();
    }
}