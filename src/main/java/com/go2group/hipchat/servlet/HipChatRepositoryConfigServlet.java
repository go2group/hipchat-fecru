package com.go2group.hipchat.servlet;

import com.atlassian.fisheye.spi.services.RepositoryService;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.go2group.hipchat.components.ConfigurationManager;
import com.go2group.hipchat.components.HipChatProxyClient;
import com.go2group.hipchat.utils.InvalidAuthTokenException;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class HipChatRepositoryConfigServlet extends HttpServlet {
//    private static final Logger log = LoggerFactory.getLogger(HipChatRepositoryConfigServlet.class);
    private static final String REPO_TEMPLATE = "/templates/repository.vm";
    private final UserManager userManager;
    private final TemplateRenderer renderer;
    private final LoginUriProvider loginUriProvider;
    private final ConfigurationManager configurationManager;
    private final RepositoryService repositoryService;
    private HipChatProxyClient hipChatProxyClient;

    public HipChatRepositoryConfigServlet(UserManager userManager, TemplateRenderer renderer,
                                          LoginUriProvider loginUriProvider, ConfigurationManager configurationManager,
                                          RepositoryService repositoryService,
                                          HipChatProxyClient hipChatProxyClient) {
        this.repositoryService = repositoryService;
        this.userManager = checkNotNull(userManager, "userManager");
        this.renderer = checkNotNull(renderer, "renderer");
        this.loginUriProvider = checkNotNull(loginUriProvider, "loginUriProvider");
        this.configurationManager = checkNotNull(configurationManager, "configurationManager");
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
        context.put("repo", repositoryService.getRepositoryInfo(req.getParameter("name")));
        context.put("roomsToNotifyStrHtml",configurationManager.getHipChatRooms(req.getParameter("name"), true));
//        context.put("test",hipChatProxyClient.notifyRoom("some room", "", "", "", StringUtils.substringBefore(req.getRequestURI().toString(),req.getContextPath())+ req.getContextPath());
        try {
	        String authToken = configurationManager.getHipChatAuthToken();
            context.put("roomsJsonHtml", hipChatProxyClient.getRooms(authToken));
        } catch (InvalidAuthTokenException e) {
            e.printStackTrace();
        }
        renderer.render(REPO_TEMPLATE, context, resp.getWriter());
    }

    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String username = userManager.getRemoteUsername(req);
        if (username == null || !userManager.isAdmin(username)) {
            redirectToLogin(req, resp);
            return;
        }

        String rooms = StringUtils.join(req.getParameterValues("roomId"),",");
        configurationManager.setNotifyRooms(req.getParameter("repoName"), rooms, true);
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