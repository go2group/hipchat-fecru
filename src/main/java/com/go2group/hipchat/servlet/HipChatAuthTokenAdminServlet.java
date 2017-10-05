package com.go2group.hipchat.servlet;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.go2group.hipchat.components.ConfigurationManager;
import com.go2group.hipchat.components.HipChatProxyClient;
import com.go2group.hipchat.components.HipChatProxyClient.JSONString;
import com.go2group.hipchat.utils.InvalidAuthTokenException;
import com.google.common.collect.Maps;

public class HipChatAuthTokenAdminServlet extends HttpServlet {
	private static final Logger log = LoggerFactory.getLogger(HipChatAuthTokenAdminServlet.class);
	private static final String ADMIN_TEMPLATE = "/templates/admin.vm";
	private final UserManager userManager;
	private final TemplateRenderer renderer;
	private final LoginUriProvider loginUriProvider;
	private final ConfigurationManager configurationManager;
	private final ApplicationProperties applicationProperties;
	private final HipChatProxyClient hipChatProxyClient;

	public HipChatAuthTokenAdminServlet(UserManager userManager, TemplateRenderer renderer,
			LoginUriProvider loginUriProvider, ConfigurationManager configurationManager,
			ApplicationProperties applicationProperties, HipChatProxyClient hipChatProxyClient) {
		this.userManager = checkNotNull(userManager, "userManager");
		this.renderer = checkNotNull(renderer, "renderer");
		this.loginUriProvider = checkNotNull(loginUriProvider, "loginUriProvider");
		this.configurationManager = checkNotNull(configurationManager, "configurationManager");
		this.applicationProperties = applicationProperties;
		this.hipChatProxyClient = hipChatProxyClient;

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
		context.put("hipChatAuthToken", configurationManager.getHipChatAuthToken());
		context.put("baseUrl", this.applicationProperties.getBaseUrl());
		context.put("serverUrl", configurationManager.getHipchatServerUrl());
		renderer.render(ADMIN_TEMPLATE, context, resp.getWriter());
	}

	@Override
	protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String username = userManager.getRemoteUsername(req);
		if (username == null || !userManager.isAdmin(username)) {
			redirectToLogin(req, resp);
			return;
		}

		resp.setContentType("text/html;charset=utf-8");
		Map<String, Object> context = Maps.newHashMap();
		context.put("baseUrl", this.applicationProperties.getBaseUrl());
		String token = req.getParameter("hipChatAuthToken");
		String serverUrl = req.getParameter("serverUrl");
		try {
			JSONString rooms = this.hipChatProxyClient.getRooms(token, serverUrl);
			configurationManager.updateConfiguration(token);
			configurationManager.updateServerUrl(serverUrl);
		} catch (InvalidAuthTokenException ie) {
			context.put("error", true);
			ie.printStackTrace();
		}

		context.put("hipChatAuthToken", token);
		context.put("serverUrl", serverUrl);
		renderer.render(ADMIN_TEMPLATE, context, resp.getWriter());
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