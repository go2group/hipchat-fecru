package com.go2group.hipchat.components;

import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.atlassian.crucible.event.ReviewCreatedEvent;
import com.atlassian.crucible.event.ReviewStateChangedEvent;
import com.atlassian.crucible.event.ReviewUpdatedEvent;
import com.atlassian.crucible.spi.PermId;
import com.atlassian.crucible.spi.data.DetailedReviewData;
import com.atlassian.crucible.spi.data.ProjectData;
import com.atlassian.crucible.spi.data.ReviewData;
import com.atlassian.crucible.spi.data.UserData;
import com.atlassian.crucible.spi.services.ProjectService;
import com.atlassian.crucible.spi.services.ReviewService;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.sal.api.ApplicationProperties;

public class CrucibleListener implements DisposableBean, InitializingBean {

	private static final Logger log = LoggerFactory.getLogger(CrucibleListener.class);
	private final ConfigurationManager configurationManager;
	private final EventPublisher eventPublisher;
	private final ApplicationProperties applicationProperties;
	private final HipChatProxyClient hipChatProxyClient;
	private final ReviewService reviewService;
	private final ProjectService projectService;

	public CrucibleListener(EventPublisher eventPublisher, ApplicationProperties applicationProperties,
			ConfigurationManager configurationManager, ReviewService reviewService, ProjectService projectService,
			HipChatProxyClient hipChatProxyClient) {
		this.eventPublisher = eventPublisher;
		this.applicationProperties = applicationProperties;
		this.configurationManager = configurationManager;
		this.hipChatProxyClient = hipChatProxyClient;
		this.reviewService = reviewService;
		this.projectService = projectService;
	}

	@EventListener
	public void onReviewCreate(ReviewCreatedEvent event) {
		PermId<ReviewData> reviewId = event.getReviewId();
		sendNotification(reviewId, "created", event.getActioner());
	}

	private void sendNotification(PermId<ReviewData> reviewId, String message, UserData userData) {

		DetailedReviewData review = this.reviewService.getReviewDetails(reviewId);
		ProjectData project = this.projectService.getProject(review.getProjectKey());
		String userUrl = applicationProperties.getBaseUrl() + "/user/" + userData.getUserName();

		String url = applicationProperties.getBaseUrl() + "/cru/" + reviewId.getId();
		String projectUrl = applicationProperties.getBaseUrl() + "/project/" + project.getKey();

		String msg = "Crucible review <a href=\"" + url + "\"><b>" + reviewId.getId() + "</b></a> " + message
				+ " in Project <a href=\"" + projectUrl + "\">" + project.getName() + "</a>" + " by <a href=\""
				+ userUrl + "\">" + userData.getDisplayName() + "</a>";

		String roomsToNotify = configurationManager.getHipChatRooms(project.getKey(), false);
		StringTokenizer rooms = new StringTokenizer(roomsToNotify, ",");

		while (rooms.hasMoreTokens()) {
			hipChatProxyClient.notifyRoom(rooms.nextToken(), msg, null);
		}
	}

	@EventListener
	public void onReviewUpdate(ReviewUpdatedEvent event) {
		PermId<ReviewData> reviewId = event.getReviewId();

		sendNotification(reviewId, "updated", event.getActioner());
	}

	@EventListener
	public void onReviewStateChange(ReviewStateChangedEvent event) {
		PermId<ReviewData> reviewId = event.getReviewId();

		sendNotification(reviewId, "status moved from <b>" + event.getOldState().name() + "</b> to <b>"
				+ event.getNewState().name() + "</b>", event.getActioner());
	}

	@Override
	public void destroy() throws Exception {
		log.debug("Unregister review event listener");
		eventPublisher.unregister(this);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		log.debug("Register review event listener");
		eventPublisher.register(this);
	}
}
