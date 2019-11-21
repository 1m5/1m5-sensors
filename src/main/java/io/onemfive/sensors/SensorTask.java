package io.onemfive.sensors;

import io.onemfive.core.util.tasks.BaseTask;
import io.onemfive.core.util.tasks.TaskRunner;

import java.util.Properties;

/**
 * A Task for the SensorsService.
 *
 * @author objectorange
 */
public abstract class SensorTask extends BaseTask {

    protected Properties properties;
    protected long periodicity = 60 * 60 * 1000; // 1 hour as default
    protected long lastCompletionTime = 0L;
    protected boolean started = false;
    protected boolean completed = false;
    protected boolean longRunning = false;

    public SensorTask(String taskName, TaskRunner taskRunner) {
        super(taskName, taskRunner);
        this.lastCompletionTime = System.currentTimeMillis();
    }

    public SensorTask(String taskName, TaskRunner taskRunner, Properties properties) {
        super(taskName, taskRunner);
        this.properties = properties;
        this.lastCompletionTime = System.currentTimeMillis();
    }

    public SensorTask(String taskName, TaskRunner taskRunner, Properties properties, long periodicity) {
       super(taskName, taskRunner);
        this.properties = properties;
        this.periodicity = periodicity;
        this.lastCompletionTime = System.currentTimeMillis();
    }

    public Boolean isLongRunning() {return longRunning;}

    public void setLastCompletionTime(long lastCompletionTime) {
        this.lastCompletionTime = lastCompletionTime;
    }

    public Long getLastCompletionTime() { return lastCompletionTime;}

    public Long getPeriodicity() {
        return periodicity;
    }
}
