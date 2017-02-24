package edu.isnap.ctd.hint;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class HintJSON {
	public static JSONObject hintToJSON(Hint hint) {
		try {
			JSONObject obj = new JSONObject();
			obj.put("from", hint.from());
			obj.put("to", hint.to());
			obj.put("type", hint.type());
			obj.put("data", hint.data());
			return obj;
		} catch (Exception e) {
			return errorToJSON(e, true);
		}
	}

	public static JSONObject errorToJSON(Exception e, boolean print) {
		if (print) e.printStackTrace();
		JSONObject error = new JSONObject();
		error.put("error", true);
		error.put("message", e.getClass().getName() + ": " +  e.getMessage());
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		error.put("stack", sw.toString());
		error.put("time", new Date().toString());
		return error;
	}

	public static JSONArray hintArray(List<? extends Hint> hints) {
		JSONArray array = new JSONArray();
		for (Hint hint : hints) {
			array.put(hintToJSON(hint));
		}
		return array;
	}
}
