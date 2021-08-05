package io.jenkins.plugins;

import java.util.logging.Level;
import hudson.Extension;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import io.jenkins.plugins.utils.GenericUtils;

@Extension
public class DevOpsQueueListener extends QueueListener {
	@Override
	public void onEnterWaiting(Queue.WaitingItem wi) {
		GenericUtils.printDebug(DevOpsQueueListener.class.getName(), "onEnterWaiting", new String[]{"itemId"}, new String[]{String.valueOf(wi.getId())}, Level.FINE);
	}
	@Override
	public void onLeaveWaiting(Queue.WaitingItem wi) {
		GenericUtils.printDebug(DevOpsQueueListener.class.getName(), "onLeaveWaiting", new String[]{"itemId"}, new String[]{String.valueOf(wi.getId())}, Level.FINE);
	}
	@Override
	public void onEnterBlocked(Queue.BlockedItem bi) {
		GenericUtils.printDebug(DevOpsQueueListener.class.getName(), "onEnterBlocked", new String[]{"itemId"}, new String[]{String.valueOf(bi.getId())}, Level.FINE);
	}
	@Override
	public void onEnterBuildable(Queue.BuildableItem bi) {
		GenericUtils.printDebug(DevOpsQueueListener.class.getName(), "onEnterBuildable", new String[]{"itemId"}, new String[]{String.valueOf(bi.getId())}, Level.FINE);
	}
	@Override
	public void onLeaveBuildable(Queue.BuildableItem bi) {
		GenericUtils.printDebug(DevOpsQueueListener.class.getName(), "onLeaveBuildable", new String[]{"itemId"}, new String[]{String.valueOf(bi.getId())}, Level.FINE);
	}
	@Override
	public void onLeft(Queue.LeftItem li) {
		GenericUtils.printDebug(DevOpsQueueListener.class.getName(), "onLeft", new String[]{"itemId"}, new String[]{String.valueOf(li.getId())}, Level.FINE);
	}
}
