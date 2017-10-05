package com.go2group.hipchat.components;

import java.net.URI;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.api.ApplicationLinkRequest;
import com.atlassian.applinks.api.ApplicationLinkRequestFactory;
import com.atlassian.applinks.api.ApplicationLinkResponseHandler;
import com.atlassian.applinks.api.ApplicationLinkService;
import com.atlassian.applinks.api.auth.types.BasicAuthenticationProvider;
import com.atlassian.crucible.spi.services.ImpersonationService;
import com.atlassian.crucible.spi.services.Operation;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.fisheye.event.CommitEvent;
import com.atlassian.fisheye.spi.data.ChangesetDataFE;
import com.atlassian.fisheye.spi.services.RevisionDataService;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.Response;
import com.atlassian.sal.api.net.ResponseException;
import com.go2group.hipchat.utils.JIRAKeyUtils;

public class FisheyeListener implements DisposableBean, InitializingBean {

	private static final String APPLINKS_JIRA = "applinks.jira";

	private static final Logger log = LoggerFactory.getLogger(FisheyeListener.class);
	private final ConfigurationManager configurationManager;
	private final EventPublisher eventPublisher;
	private final ApplicationProperties applicationProperties;
	private final HipChatProxyClient hipChatProxyClient;
	private final RevisionDataService revisionDataService;
	private final ApplicationLinkService applicationLinkService;
	private final ImpersonationService impersonationService;

	public FisheyeListener(EventPublisher eventPublisher, ApplicationProperties applicationProperties,
			ConfigurationManager configurationManager, RevisionDataService revisionDataService,
			HipChatProxyClient hipChatProxyClient, ApplicationLinkService applicationLinkService,
			ImpersonationService impersonationService) {
		this.eventPublisher = eventPublisher;
		this.applicationProperties = applicationProperties;
		this.configurationManager = configurationManager;
		this.revisionDataService = revisionDataService;
		this.hipChatProxyClient = hipChatProxyClient;
		this.applicationLinkService = applicationLinkService;
		this.impersonationService = impersonationService;
	}

	@EventListener
	public void onCommit(final CommitEvent event) throws Throwable {
		final String repoName = event.getRepositoryName();

		impersonationService.doPrivilegedAction(null, new Operation<Object, Throwable>() {
			@Override
			public Object perform() throws Throwable {
				String changeSetId = event.getChangeSetId();
				ChangesetDataFE cs = revisionDataService.getChangeset(repoName, changeSetId);
				String commitMessage = cs.getComment();

				List<String> jiraKeys = JIRAKeyUtils.getKeys(commitMessage);
				if (jiraKeys.size() > 0) {
					try {
						Iterable<ApplicationLink> links = applicationLinkService.getApplicationLinks();
						for (ApplicationLink applicationLink : links) {
							if (APPLINKS_JIRA.equals(applicationLink.getType().getI18nKey())) {
								URI jiraUrl = applicationLink.getRpcUrl();

								ApplicationLinkRequestFactory requestFactory = applicationLink
										.createAuthenticatedRequestFactory(BasicAuthenticationProvider.class);

								if (requestFactory != null) {
									for (String jiraKey : jiraKeys) {
										log.debug("Found issue Key:" + jiraKey);
										ApplicationLinkRequest request = requestFactory.createRequest(
												Request.MethodType.GET, "/rest/api/2/issue/" + jiraKey);
										boolean validIssue = request
												.execute(new ApplicationLinkResponseHandler<Boolean>() {

													public Boolean handle(Response response) throws ResponseException {
														return response.isSuccessful();
													}

													public Boolean credentialsRequired(Response response)
															throws ResponseException {
														return false;
													}

												});
										if (validIssue) {
											commitMessage = commitMessage.replaceAll("\\b" + jiraKey + "\\b",
													" <a href=\"" + jiraUrl.toString() + "/browse/" + jiraKey + "\">"
															+ jiraKey + "</a> ");
										}
									}
								} else {
									log.error("JIRA Issue key found in commit message but Basic Authentication not configured under Application Links");
								}
							}
						}
					} catch (Exception e) {
						// Not too worried about errors here! Just printing to
						// console
						// for debugging and proceeding with core functionality
						e.printStackTrace();
					}

				}

				String url = applicationProperties.getBaseUrl() + "/changelog/" + repoName + "?cs=" + changeSetId;
				/*
				 * String userUrl = "#"; UserData user = null; try { user =
				 * userService.getMappedUser(repoName, cs.getAuthor()); userUrl
				 * = applicationProperties.getBaseUrl() + "/user/" +
				 * user.getUserName(); } catch (ServerException e) { // TODO
				 * Auto-generated catch block e.printStackTrace(); } String msg
				 * = "<b>" + (user != null ? ("<a href=\"" + userUrl + "\">" +
				 * cs.getAuthor() + "</a>") : cs.getAuthor()) + "</b> - " +
				 * commitMessage + ". In CL <a href=\"" + url + "\">" +
				 * (changeSetId.length() > 12 ? changeSetId.substring(0, 12) :
				 * changeSetId) + "</a> to " + "<a href=\"" +
				 * applicationProperties.getBaseUrl() + "/browse/" + repoName +
				 * "\">" + repoName + "</a> in " + cs.getBranch();
				 */

				String msg = "<b>" + cs.getAuthor() + "</b> - " + commitMessage + ". In CL <a href=\"" + url + "\">"
						+ (changeSetId.length() > 12 ? changeSetId.substring(0, 12) : changeSetId) + "</a> to "
						+ "<a href=\"" + applicationProperties.getBaseUrl() + "/browse/" + repoName + "\">" + repoName
						+ "</a> in " + cs.getBranch();

				String roomsToNotify = configurationManager.getHipChatRooms(event.getRepositoryName(), true);
				StringTokenizer rooms = new StringTokenizer(roomsToNotify, ",");

				while (rooms.hasMoreTokens()) {
					hipChatProxyClient.notifyRoom(rooms.nextToken(), msg, null);
				}
				return null;
			}
		});
	}

	@Override
	public void destroy() throws Exception {
		log.debug("Unregister commit event listener");
		eventPublisher.unregister(this);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		log.debug("Register commit event listener");
		eventPublisher.register(this);
	}
}
