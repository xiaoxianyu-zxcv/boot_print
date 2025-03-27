package org.example.print.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 调度器配置类
 */
@Configuration
public class SchedulerConfig {

    /**
     * 创建ScheduledExecutorService实例
     * 用于执行定时任务，包括WebSocket心跳
     */
    @Bean
    public ScheduledExecutorService scheduledExecutor() {
        // 使用守护线程创建调度线程池，确保应用关闭时能正常退出
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            t.setName("scheduler-thread");
            return t;
        });
    }

}