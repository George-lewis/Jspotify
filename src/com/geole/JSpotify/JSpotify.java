package com.geole.JSpotify;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.geole.JSpotify.Models.Status;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

public class JSpotify {
	
	public static class SpotifyException extends Exception {

		private static final long serialVersionUID = 7267790627241968134L;

		public SpotifyException(String message) {
			super(message);
		}

		public SpotifyException(String message, Throwable throwable) {
			super(message, throwable);
		}

	}
	
	public static final String VERSION = "v0.1";
	
	private static final Map<String, Object> EMPTY_MAP = Collections.emptyMap();
	
	private static String OAuthToken, CSRFToken;

	private static String baseURL = "http://JSpotify.spotilocal.com:{port}";

	private static int port = -1;
	
	private static boolean initialized = false;

	private static final HashMap<Integer, String> Errors = new HashMap<>();

	static {

		// Errors
		// Sourced from https://github.com/chrippa/spotify-remote
		Errors.put(4001, "Unknown method");
		Errors.put(4002, "Error parsing request");
		Errors.put(4003, "Unknown service");
		Errors.put(4004, "Service not responding");
		Errors.put(4102, "Invalid OAuthToken");
		Errors.put(4103, "Expired OAuth token");
		Errors.put(4104, "OAuth token not verified");
		Errors.put(4105, "Token verification denied, too many requests");
		Errors.put(4106, "Token verification timeout");
		Errors.put(4107, "Invalid Csrf token");
		Errors.put(4108, "OAuth token is invalid for current user");
		Errors.put(4109, "Invalid Csrf path");
		Errors.put(4110, "No user logged in");
		Errors.put(4111, "Invalid scope");
		Errors.put(4112, "Csrf challenge failed");
		Errors.put(4201, "Upgrade to premium");
		Errors.put(4202, "Upgrade to premium or wait");
		Errors.put(4203, "Billing failed");
		Errors.put(4204, "Technical error");
		Errors.put(4205, "Commercial is playing");
		Errors.put(4301, "Content is unavailable but can be purchased");
		Errors.put(4302, "Premium only content");
		Errors.put(4303, "Content unavailable");

	}

	// This is a static class
	private JSpotify() {}
	
	public static boolean initialize() throws SpotifyException {
		
		// Acquire OAuth token from static url and cache result
		try {
			JSpotify.OAuthToken = Unirest.get("https://open.spotify.com/token")
					.asJson()
					.getBody()
					.getObject()
					.getString("t");
		} catch (JSONException | UnirestException e) {
			throw new SpotifyException("Failed to acquire OAuth token", e);
		}
		
		// Resolve port
		// Value ranges taken from
		// https://medium.com/@bengreenier/hijacking-spotify-web-control-5014b0a1a360
		// Reverse order because I find the port number tends to be closer to 4400 than
		// 4370
		for (int i = 4400; i >= 4370; i--) {
			try {
				URL url = new URL(JSpotify.baseURL.replace("{port}", Integer.toString(i)));
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.connect();
				if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
					throw new IOException("Not a good connection!");
				}
			} catch (IOException e) {
				// Unable to find port
				if (i == 4370) {
					throw new SpotifyException("Unable to determine Spotify port. Is Spotify running?", e);
				}
				// Not the correct port - continue
				continue;
			}
			JSpotify.baseURL = JSpotify.baseURL.replace("{port}", Integer.toString(i));
			JSpotify.port = i;
			break;
		}
		
		try {
			
			JSONObject obj = Unirest.get(baseURL + "/simplecsrf/token.json")
					.header("Origin", "https://open.spotify.com")
					.asJson().getBody().getObject();
			
			if (obj.has("error")) {
				JSONObject error = obj.getJSONObject("error");
				throw new SpotifyException("Spotify client returned an error!\nError code: " + error.getString("type") + "\nMessage: " + error.getString("message"));
			}
			
			JSpotify.CSRFToken = obj.getString("token");
		} catch (JSONException | UnirestException e) {
			throw new SpotifyException("Failed to acquire CSRF token.", e);
		}
				
		JSpotify.initialized = true;
				
		return true;
				
	}
	
	private static <C> C request(String path, Map<String, Object> opts, Class<C> clazz) throws SpotifyException {
		
		if (!initialized) {
			throw new SpotifyException("JSpotify must be initialized before you can use it.");
		}
		
		try {
			
		JSONObject obj =  Unirest.get(baseURL + path).queryString("oauth", OAuthToken).queryString("csrf", CSRFToken).queryString(opts).asJson().getBody().getObject();
		
		if (obj.has("error")) {
			JSONObject error = obj.getJSONObject("error");
			String error_code = error.getString("type");
			String message = error.has("message") ? error.getString("message") : Errors.get(Integer.parseInt(error_code));
			throw new SpotifyException("Spotify client returned an error!\nError code " + error_code + "\nMessage: " + message);
		}
		
		if (clazz.equals(Void.TYPE)) {
			return null;
		} else {
			return clazz.getConstructor(JSONObject.class).newInstance(obj);
		}
		
		} catch (UnirestException | JSONException e) {
			throw new SpotifyException("Failed to perform api call.", e);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new SpotifyException("Failed to instantiate the model class during api call.", e);
		}
		
	}

	public static String getOAuthToken() {
		return JSpotify.OAuthToken;
	}

	public static String getCSRFToken() {
		return JSpotify.CSRFToken;
	}
	
	public static boolean isInitialized() {
		return JSpotify.initialized;
	}

	public static int getPort() {
		return JSpotify.port;
	}
	
	/* Seems to not work - additional testing required
	public static void enque(String... urls) throws SpotifyException {
		for (String url : urls) {
			JSpotify.request("/remote/play.json", Map.of("uri", url, "action", "queue"), Void.TYPE);
		}
	}*/

	public static Status play(String url) throws SpotifyException {
		return JSpotify.request("/remote/play.json", Map.of("uri", url), Status.class);
	}

	public static Status pause() throws SpotifyException {
		return JSpotify.request("/remote/pause.json", EMPTY_MAP, Status.class);
	}
	
	public static Status unpause() throws SpotifyException {
		return JSpotify.request("/remote/pause.json", EMPTY_MAP, Status.class);
	}
	
	public static Status getStatus() throws SpotifyException {
		return JSpotify.request("/remote/status.json", EMPTY_MAP, Status.class);
	}

}
