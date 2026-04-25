package com.itao.workflowEngine.graph.thread;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.itao.workflowEngine.graph.model.NodeRunResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class RunGraphNodeThreadPool {

    @Value("${workflow.graph-engine.core-pool-size:4}")
    private int corePoolSize;

    @Value("${workflow.graph-engine.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${workflow.graph-engine.queue-size:32}")
    private int queueSize;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("workflow-node-%d").build();
        executorService = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                threadFactory
        );
    }

    public CompletableFuture<NodeRunResult> submitNodeTask(RunGraphNodeTask task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }, executorService);
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
