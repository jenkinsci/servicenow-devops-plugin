package io.jenkins.plugins;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;
import javax.servlet.http.HttpServletResponse;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.RootAction;
import io.jenkins.plugins.model.DevOpsJobModel;
import io.jenkins.plugins.utils.DevOpsConstants;
import io.jenkins.plugins.utils.GenericUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;


import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.json.JsonHttpResponse;
import org.kohsuke.stapler.verb.GET;


@Extension
public class DevOpsJSONAPI implements RootAction {

	private static final HashMap<String, List<DevOpsJobModel>> jobsPageCache = new HashMap<>();

	private static void addJobsToCache(String pageKey, List<DevOpsJobModel> jobs) {
		synchronized (jobsPageCache) {
			jobsPageCache.put(pageKey, jobs);
		}
	}

	private static List<DevOpsJobModel> getJobsFromCache(String pageKey) {
		synchronized (jobsPageCache) {
			return jobsPageCache.getOrDefault(pageKey, null);
		}
	}

	private static boolean isPageConfigExistsInCache(String pageKey) {
		synchronized (jobsPageCache) {
			return jobsPageCache.containsKey(pageKey);
		}
	}

	private static void removeJobsFromCache(String pageKey) {
		synchronized (jobsPageCache) {
			if (jobsPageCache.containsKey(pageKey)) {
				jobsPageCache.remove(pageKey);
			}
		}
	}

	@CheckForNull
	@Override
	public String getIconFileName() {
		return null;
	}

	@CheckForNull
	@Override
	public String getDisplayName() {
		return null;
	}

	@Override
	public String getUrlName() {
		return DevOpsConstants.SN_DEVOPS_DISCOVER_API_BASE_URL.toString();
	}

	private DevOpsJobModel createDevOpsJobFromItem(AbstractItem item) {
		DevOpsJobModel job = new DevOpsJobModel();
		job.set_class(item.getClass().getName());
		job.setDisplayName(item.getDisplayName());
		job.setName(item.getFullName());
		job.setFullName(item.getFullName());
		job.setUrl(item.getAbsoluteUrl());
		return job;
	}

	private List<DevOpsJobModel> getAllJobs(Integer depth) throws Exception {
		Jenkins instance = Jenkins.getInstanceOrNull();
		if (instance == null) {
			GenericUtils.printDebug(DevOpsJSONAPI.class.getName(), "getAllJobs", new String[]{"message"},
					new String[]{"No Instance Found"}, Level.WARNING);
			throw new Exception("No Instance Found");
		}
		return instance.getAllItems(AbstractItem.class,
				abstractItem -> ((depth == null || abstractItem.getFullName().split("/").length <= depth)
						&& (abstractItem.getParent().getClass().getName().endsWith("Folder")
						|| abstractItem.getParent().getClass().getName().endsWith("Hudson"))
				)
		).stream().map(item -> this.createDevOpsJobFromItem(item)).collect(Collectors.toList());
	}

	@GET
	@WebMethod(name = "is-devops-api-present")
	public JsonHttpResponse isDevOpsAPIPresent() {
		return new JsonHttpResponse(JSONObject.fromObject(true), HttpServletResponse.SC_OK);
	}

	@GET
	@WebMethod(name = "jobs")
	public JsonHttpResponse getJobs(@QueryParameter(required = true) Integer pageNumber,
	                                @QueryParameter(required = true) Integer pageSize,
	                                @QueryParameter(required = true) Integer depth,
	                                @QueryParameter(required = true) String importRequest) throws Exception {
		JSONObject response = new JSONObject();
		List<DevOpsJobModel> jobs = getJobsFromCache(importRequest);
		if (jobs == null) {
			jobs = this.getAllJobs(depth);
			addJobsToCache(importRequest, jobs);
		}
		int totalPage = jobs.size() / pageSize;
		if (totalPage < pageNumber) {
			GenericUtils.printDebug(DevOpsJSONAPI.class.getName(), "getJobs", new String[]{"message"},
					new String[]{"pageNumber is larger than total pages"}, Level.WARNING);
			throw new Exception("pageNumber is larger than total pages");
		}
		int firstIndex = pageNumber * pageSize;
		int lastIndex = pageNumber == totalPage ? jobs.size() : firstIndex + pageSize;
		boolean isLastPage = lastIndex == jobs.size();
		JSONArray jobList = JSONArray.fromObject(jobs.subList(firstIndex, lastIndex));
		response.put("jobs", jobList);
		response.put("hasNext", !isLastPage);
		if (isLastPage) {
			removeJobsFromCache(importRequest);
		}
		return new JsonHttpResponse(response, HttpServletResponse.SC_OK);
	}
/*
	@GET
	@WebMethod(name = "get-all-jobs")
	public JsonHttpResponse getAllJobs(
			@QueryParameter Integer first,
			@QueryParameter Integer last,
			@QueryParameter Integer depth) {

		JSONObject response = new JSONObject();
		try {
			List<DevOpsJobModel> jobs = this.getAllJobs(depth);
			if (null == jobs) {
				throw new JsonHttpResponse(JSONObject.fromObject("No instance found"));
			}
			if (jobs != null) {
				int firstIndex = first != null ? first : 0;
				int lastIndex = (last != null && last < jobs.size()) ? last : jobs.size();
				if (firstIndex >= jobs.size()) {
					response.put("jobs", new ArrayList<>());
					response.put("hasNext", false);
				} else {
					JSONArray jobList = JSONArray.fromObject(jobs.subList(firstIndex, lastIndex));
					response.put("jobs", jobList);
					response.put("hasNext", lastIndex + 1 < jobs.size());
				}
			}
		} catch(Exception ex) {
			throw new JsonHttpResponse(JSONObject.fromObject(ex.getMessage()));
		}
		return new JsonHttpResponse(response, HttpServletResponse.SC_OK);
	}*/
}
