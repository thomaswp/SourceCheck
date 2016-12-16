package edu.isnap.ctd.hint;

import org.json.JSONObject;

public interface Hint {
	String to();
	String from();
	JSONObject data();
	String type();
}
