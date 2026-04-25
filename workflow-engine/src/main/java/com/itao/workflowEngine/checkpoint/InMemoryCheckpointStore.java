package com.itao.workflowEngine.checkpoint;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryCheckpointStore implements CheckpointStore {

    private final Map<String, GraphCheckpointSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public String save(GraphCheckpointSnapshot snapshot) {
        String checkpointId = snapshot.getCheckpointId();
        if (checkpointId == null || checkpointId.isBlank()) {
            checkpointId = "ckpt-" + UUID.randomUUID().toString().substring(0, 8);
            snapshot.setCheckpointId(checkpointId);
        }
        snapshots.put(checkpointId, snapshot);
        return checkpointId;
    }

    @Override
    public GraphCheckpointSnapshot load(String checkpointId) {
        return snapshots.get(checkpointId);
    }
}
