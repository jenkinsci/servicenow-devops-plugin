package io.jenkins.plugins;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import io.jenkins.plugins.model.DevOpsModel;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;


/**
 * ServiceNow DevOps task dispatcher to determine whether a given Job is under change control and should be blocked in the queue while waiting for approval.
 *
 * Queue stages:
 * (enter) -- waitingList --+-- blockedProjects
                           |
                           |        |
                           |
                           +-- buildables --- pending --- left
                                                   |
                                    |              |
                                    +---(rarely)---+
 *
 */
@Extension
public class DevOpsQueueTaskDispatcher<P, R> extends QueueTaskDispatcher {
	@SuppressWarnings({"static-access", "unused"})
	DevOpsModel model;
	/**
	 * Called whenever Queue is considering if Queue.Item is ready to execute immediately (which doesn't necessarily mean that it gets executed right away;
	 * it's still subject to executor availability), or if it should be considered blocked.
	 * Compared to canTake(), this version tells Jenkins that the task is simply not ready to execute, even if there's available executor.
	 * This is more efficient than canTake(), and it sends the right signal to Jenkins so that it won't use Cloud to try to provision new executors.
	 * <p>
	 * Vetos are additive. When multiple QueueTaskDispatcher's are in the system, the task is considered blocked if any one of them returns a non-null value.
	 * <p>
	 * If a QueueTaskDispatcher returns non-null from this method, the task is placed into the 'blocked' state, and generally speaking it stays
	 * in this state for a few seconds before its state gets re-evaluated.
	 *
	 * @param item the item being evaluated by the dispatcher
	 * @return null to indicate that the item is ready to proceed to the buildable state as far as this
	 * QueueTaskDispatcher is concerned. Otherwise return an object that indicates why
	 * the build is blocked.
	 */
	@Override
	public CauseOfBlockage canRun(Queue.Item item) {
		model = new DevOpsModel();
		if (item.task instanceof Job<?, ?>) {
			Job<?, ?> job = (Job<?, ?>) item.task;
			String pronoun = job.getPronoun();
			GenericUtils.printDebug(DevOpsQueueTaskDispatcher.class.getName(), "canRun",
					new String[]{"pronoun"}, new String[]{pronoun}, model.isDebug());
			// Pipeline - gating done at the step execution level
			if (pronoun.equalsIgnoreCase(DevOpsConstants.PIPELINE_PRONOUN.toString()) ||
				pronoun.equalsIgnoreCase(
						DevOpsConstants.BITBUCKET_MULTI_BRANCH_PIPELINE_PRONOUN.toString()))
				return null;
				// Freestyle - gating done at the queue dispatcher level
			else if (pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_PRONOUN.toString()) ||
					pronoun.equalsIgnoreCase(DevOpsConstants.FREESTYLE_MAVEN_PRONOUN.toString()))
				return model.handleFreestyle(item, job);
				// If not a supported type, don't block the Job
			else
				return null;
		}
		// Disabled ("ServiceNow DevOps" check box=false)
		return null;
	}

}
