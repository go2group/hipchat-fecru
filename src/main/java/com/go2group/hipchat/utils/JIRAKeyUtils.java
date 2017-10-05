package com.go2group.hipchat.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JIRAKeyUtils {
	
	private static final String KEY_REGEX = "[A-Z][A-Z0-9]+-[0-9]+";
	
	public static List<String> getKeys(String commitMessage){
		List<String> keys = new ArrayList<String>();
		
		Pattern pattern = Pattern.compile(KEY_REGEX);
		Matcher matcher = pattern.matcher(commitMessage);
		while (matcher.find()){
			keys.add(matcher.group());
		}
		
		return keys;
	}

}
