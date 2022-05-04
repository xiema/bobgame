package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.gdx.ai.GdxAI;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import com.badlogic.gdx.ai.utils.random.FloatDistribution;
import com.esotericsoftware.minlog.Log;

/**
 * Starts an internal timer when this task is first run or after its timer runs out. This task always succeeds while the timer is running, and
 * fails when the timer ends, unless {@link #failedDuring} is set to true.
 */
public class Timer<E> extends LeafTask<E> implements LibraryTask<E> {
    @TaskAttribute(required = true)
    public FloatDistribution seconds;
    @TaskAttribute
    public boolean failedDuring = false;

    private float timeout = 0;
    private float startTime;
    private boolean isRunning = false;

    public Timer() {
        super();
    }

    @Override
    public Status execute() {
        float currentTime = GdxAI.getTimepiece().getTime();
        if (!isRunning) {
            isRunning = true;
            startTime = currentTime;
            timeout = seconds.nextFloat();
        }
        if (currentTime - startTime < timeout) {
            return failedDuring ? Status.FAILED : Status.SUCCEEDED;
        }
        else {
            isRunning = false;
            return failedDuring ? Status.SUCCEEDED : Status.FAILED;
        }
    }

    @Override
    protected Task<E> copyTo(Task<E> task) {
        Timer<E> otherTask = (Timer<E>) task;
        otherTask.seconds = seconds;
        otherTask.failedDuring = failedDuring;
        return task;
    }

    @Override
    public void resetTask() {
        super.resetTask();
        isRunning = false;
    }

    @Override
    public void reset() {
        super.reset();
        seconds = null;
        isRunning = false;
        failedDuring = false;
    }

    @Override
    public Task<E> copyToTask(Task<E> task) {
        return copyTo(task);
    }
}
