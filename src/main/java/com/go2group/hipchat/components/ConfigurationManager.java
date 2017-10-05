package com.go2group.hipchat.components;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.opensymphony.util.TextUtils;

public class ConfigurationManager {
    // TODO for some reason FeCru throws up when createSettingsForKey is non null. So, I'm gonna disable this for
    // now and attach the auth-token to the global space
    // private static final String PLUGIN_STORAGE_KEY = "com.atlassian.labs.hipchat";
    private static final String PLUGIN_STORAGE_KEY = null;
    private static final String HIPCHAT_AUTH_TOKEN_KEY = "hipchat-auth-token";
    private static final String HIPCHAT_SERVER_URL = "hipchat-server-url";
	private static final String HIPCHAT_KEY_REPO_PREFIX = "hipchat-repo-";
	private static final String HIPCHAT_KEY_PROJECT_PREFIX = "hipchat-project-";

    private final PluginSettingsFactory pluginSettingsFactory;

    public ConfigurationManager(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    public String getHipChatAuthToken() {
        return getValue(HIPCHAT_AUTH_TOKEN_KEY);
    }
    
    public String getHipchatServerUrl() {
        String serverUrl = getValue(HIPCHAT_SERVER_URL);
		return TextUtils.stringSet(serverUrl) ? serverUrl : "https://api.hipchat.com";
    }
    
    public String getHipChatRooms(String name, boolean isFisheye) {
		return isFisheye ? getValue(HIPCHAT_KEY_REPO_PREFIX + name) : getValue(HIPCHAT_KEY_PROJECT_PREFIX + name);
	}

    private String getValue(String storageKey) {
        PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
        Object storedValue = settings.get(storageKey);
        return storedValue == null ? "" : storedValue.toString();
    }

    public void updateConfiguration(String authToken) {
        PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
        settings.put(HIPCHAT_AUTH_TOKEN_KEY, authToken);
    }
    
    public void updateServerUrl(String serverUrl) {
        PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
        settings.put(HIPCHAT_SERVER_URL, serverUrl);
    }

    public void setNotifyRooms(String name, String rooms, boolean isFisheye) {
		PluginSettings settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
		if (isFisheye) {
			settings.put(HIPCHAT_KEY_REPO_PREFIX + name, rooms);
		} else {
			settings.put(HIPCHAT_KEY_PROJECT_PREFIX + name, rooms);
		}
	}

}