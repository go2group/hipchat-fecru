package com.go2group.hipchat.servlet;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.crucible.spi.data.ProjectData;
import com.atlassian.crucible.spi.services.ProjectService;
import com.atlassian.fisheye.spi.admin.data.RepositoryData;
import com.atlassian.fisheye.spi.admin.services.RepositoryAdminService;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.go2group.hipchat.components.ConfigurationManager;
import com.go2group.hipchat.components.HipChatProxyClient;
import com.go2group.hipchat.components.HipChatProxyClient.JSONString;
import com.go2group.hipchat.utils.InvalidAuthTokenException;
import com.google.common.collect.Maps;

public class AnnouncementServlet extends HttpServlet {
	private static final Logger log = LoggerFactory.getLogger(AnnouncementServlet.class);

	private static final String ANNOUNCEMENT_TEMPLATE = "/templates/announcement.vm";
	private final UserManager userManager;
	private final TemplateRenderer renderer;
	private final LoginUriProvider loginUriProvider;
	private HipChatProxyClient hipChatProxyClient;
	private final ApplicationProperties applicationProperties;
	private final RepositoryAdminService repositoryService;
	private final ConfigurationManager configurationManager;
	private final ProjectService projectService;

	public AnnouncementServlet(UserManager userManager, TemplateRenderer renderer, LoginUriProvider loginUriProvider,
			HipChatProxyClient hipChatProxyClient, ApplicationProperties applicationProperties,
			RepositoryAdminService repositoryService, ConfigurationManager configurationManager, ProjectService projectService) {
		this.userManager = checkNotNull(userManager, "userManager");
		this.renderer = checkNotNull(renderer, "renderer");
		this.loginUriProvider = checkNotNull(loginUriProvider, "loginUriProvider");
		this.hipChatProxyClient = hipChatProxyClient;
		this.applicationProperties = applicationProperties;
		this.repositoryService = repositoryService;
		this.configurationManager = configurationManager;
		this.projectService = projectService;
	}

	@Override
	protected final void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String username = userManager.getRemoteUsername(req);
		if (username == null || !userManager.isAdmin(username)) {
			redirectToLogin(req, resp);
			return;
		}

		resp.setContentType("text/html;charset=utf-8");
		Map<String, Object> context = Maps.newHashMap();
		try {
	        String authToken = configurationManager.getHipChatAuthToken();
			context.put("roomsJsonHtml", hipChatProxyClient.getRooms(authToken));
		} catch (InvalidAuthTokenException e) {
			e.printStackTrace();
		}
		context.put("baseUrl", this.applicationProperties.getBaseUrl());
		renderer.render(ANNOUNCEMENT_TEMPLATE, context, resp.getWriter());
	}

	@Override
	protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String username = userManager.getRemoteUsername(req);
		if (username == null || !userManager.isAdmin(username)) {
			redirectToLogin(req, resp);
			return;
		}

		String message = req.getParameter("message");
		if (message != null) {
			String roomOption = req.getParameter("roomOption");
			String color = req.getParameter("color");
			if ("all".equals(roomOption)) {
		        String authToken = configurationManager.getHipChatAuthToken();
				JSONString roomString = hipChatProxyClient.getRooms(authToken);
				JSONObject rooms;
				try {
					rooms = new JSONObject(roomString.toString());
					if (rooms != null) {
						JSONArray roomArray = rooms.getJSONArray("rooms");
						for (int i = 0; i < roomArray.length(); i++) {
							JSONObject room = roomArray.getJSONObject(i);
							this.hipChatProxyClient.notifyRoom(room.getString("room_id"), message, color);
						}
					}
				} catch (JSONException e) {
					log.error("Error parsing room json:"+roomString.toString(), e);
					e.printStackTrace();
				}
			} else if ("subscribed".equals(roomOption)) {
				Set<String> rooms = new HashSet<String>();
				Set<RepositoryData> repos = this.repositoryService.getRepositories();
				for (RepositoryData repo : repos) {
					String roomString = this.configurationManager.getHipChatRooms(repo.getName(), true);
					rooms.addAll(Arrays.asList(StringUtils.split(StringUtils.defaultIfEmpty(roomString, ""), ",")));
				}
				List<ProjectData> projects = this.projectService.getAllProjects();
				for (ProjectData project : projects) {
					String roomString = this.configurationManager.getHipChatRooms(project.getKey(), false);
					rooms.addAll(Arrays.asList(StringUtils.split(StringUtils.defaultIfEmpty(roomString, ""), ",")));
				}
				for (String room : rooms) {
					this.hipChatProxyClient.notifyRoom(room, message, color);
				}
			} else {
				String[] rooms = req.getParameterValues("roomId");
				for (String room : rooms) {
					this.hipChatProxyClient.notifyRoom(room, message, color);
				}
			}
		}
		resp.sendRedirect(req.getRequestURL().toString() + "?" + req.getQueryString());
	}

	private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.sendRedirect(loginUriProvider.getLoginUri(getUri(request)).toASCIIString());
	}

	private URI getUri(HttpServletRequest request) {
		StringBuffer builder = request.getRequestURL();
		if (request.getQueryString() != null) {
			builder.append("?");
			builder.append(request.getQueryString());
		}
		return URI.create(builder.toString());
	}

}