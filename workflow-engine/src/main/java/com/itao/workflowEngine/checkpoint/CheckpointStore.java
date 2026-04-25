package com.itao.workflowEngine.checkpoint;

public interface CheckpointStore {

    String save(GraphCheckpointSnapshot snapshot);

    GraphCheckpointSnapshot load(String checkpointId);
}
