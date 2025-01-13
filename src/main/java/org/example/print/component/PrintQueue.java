package org.example.print.component;


import org.example.print.bean.PrintTask;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class PrintQueue {
    private final BlockingQueue<PrintTask> queue = new LinkedBlockingQueue<>();

    public boolean offer(PrintTask task) {
        return queue.offer(task);
    }

    public PrintTask poll() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}