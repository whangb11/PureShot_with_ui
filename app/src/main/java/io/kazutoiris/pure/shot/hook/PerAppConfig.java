package io.kazutoiris.pure.shot.hook;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Immutable, versioned per-application configuration.
 *
 * <p>The wire format only stores explicit overrides:
 *
 * <pre>{@code
 * {
 *   "version": 1,
 *   "apps": {
 *     "com.example.app": {
 *       "master": "off",
 *       "features": { "status_bar": true }
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Decoding is intentionally tolerant: unknown feature keys, illegal value types and broken
 * entries are skipped with a warning instead of failing the whole configuration. A payload from a
 * future format version is ignored entirely. Neither method ever throws.
 */
public final class PerAppConfig {

	public static final int VERSION = 1;

	/** Guard against absurd payloads arriving through RemotePreferences. */
	public static final int MAX_JSON_LENGTH = 512 * 1024;

	private static final String EMPTY_JSON = "{\"version\":1,\"apps\":{}}";
	private static final String KEY_VERSION = "version";
	private static final String KEY_APPS = "apps";
	private static final String KEY_MASTER = "master";
	private static final String KEY_FEATURES = "features";
	private static final int MAX_PACKAGE_NAME_LENGTH = 255;

	public static final PerAppConfig EMPTY =
			new PerAppConfig(Collections.emptyMap());

	private final Map<String, AppPolicy> apps;

	public PerAppConfig(Map<String, AppPolicy> apps) {
		TreeMap<String, AppPolicy> sorted = new TreeMap<>();
		if (apps != null) {
			for (Map.Entry<String, AppPolicy> entry : apps.entrySet()) {
				String packageName = entry.getKey();
				AppPolicy policy = entry.getValue();
				if (packageName != null && !packageName.isEmpty()
						&& policy != null && !policy.isEmpty()) {
					sorted.put(packageName, policy);
				}
			}
		}
		this.apps = Collections.unmodifiableMap(sorted);
	}

	public Map<String, AppPolicy> getApps() {
		return apps;
	}

	public AppPolicy get(String packageName) {
		return packageName != null ? apps.get(packageName) : null;
	}

	public boolean isEmpty() {
		return apps.isEmpty();
	}

	public PerAppConfig withPolicy(String packageName, AppPolicy policy) {
		if (packageName == null || packageName.isEmpty()) {
			return this;
		}
		if (policy == null || policy.isEmpty()) {
			return without(packageName);
		}
		Map<String, AppPolicy> copy = new LinkedHashMap<>(apps);
		copy.put(packageName, policy);
		return new PerAppConfig(copy);
	}

	public PerAppConfig without(String packageName) {
		if (packageName == null || !apps.containsKey(packageName)) {
			return this;
		}
		Map<String, AppPolicy> copy = new LinkedHashMap<>(apps);
		copy.remove(packageName);
		return new PerAppConfig(copy);
	}

	/**
	 * Serializes only non-inherited values. Never throws; a failure returns the empty payload.
	 */
	public String encode() {
		try {
			JSONObject root = new JSONObject();
			root.put(KEY_VERSION, VERSION);
			JSONObject appsObject = new JSONObject();
			for (Map.Entry<String, AppPolicy> entry : apps.entrySet()) {
				AppPolicy policy = entry.getValue();
				if (policy == null || policy.isEmpty()) {
					continue;
				}
				JSONObject appObject = new JSONObject();
				if (policy.getMaster() != Override.INHERIT) {
					appObject.put(KEY_MASTER, policy.getMaster().getKey());
				}
				if (!policy.getFeatures().isEmpty()) {
					JSONObject featuresObject = new JSONObject();
					for (Feature feature : Feature.configurable()) {
						Boolean value = policy.getFeatureOverride(feature);
						if (value != null) {
							featuresObject.put(feature.getKey(), value.booleanValue());
						}
					}
					appObject.put(KEY_FEATURES, featuresObject);
				}
				appsObject.put(entry.getKey(), appObject);
			}
			root.put(KEY_APPS, appsObject);
			return root.toString();
		} catch (JSONException e) {
			return EMPTY_JSON;
		}
	}

	public static PerAppConfig decode(String json) {
		return decode(json, null);
	}

	/**
	 * @param warning receives one message per ignored or malformed entry; may be {@code null}.
	 */
	public static PerAppConfig decode(String json, Consumer<String> warning) {
		if (json == null || json.isEmpty()) {
			return EMPTY;
		}
		if (json.length() > MAX_JSON_LENGTH) {
			warn(warning, "per_app_config exceeds " + MAX_JSON_LENGTH + " characters");
			return EMPTY;
		}
		try {
			JSONObject root = new JSONObject(json);

			Object rawVersion = root.opt(KEY_VERSION);
			if (rawVersion != null && rawVersion != JSONObject.NULL) {
				if (!(rawVersion instanceof Number)) {
					warn(warning, "per_app_config has a non-numeric version");
					return EMPTY;
				}
				int version = ((Number) rawVersion).intValue();
				if (version < 1 || version > VERSION) {
					warn(warning, "per_app_config version " + version + " is not supported");
					return EMPTY;
				}
			}

			JSONObject appsObject = root.optJSONObject(KEY_APPS);
			if (appsObject == null) {
				return EMPTY;
			}

			Map<String, AppPolicy> parsed = new LinkedHashMap<>();
			Iterator<String> packageNames = appsObject.keys();
			while (packageNames.hasNext()) {
				String packageName = packageNames.next();
				if (packageName.isEmpty() || packageName.length() > MAX_PACKAGE_NAME_LENGTH) {
					warn(warning, "per_app_config has an invalid package name");
					continue;
				}
				JSONObject appObject = appsObject.optJSONObject(packageName);
				if (appObject == null) {
					warn(warning, "per_app_config entry for " + packageName + " is not an object");
					continue;
				}
				AppPolicy policy = decodePolicy(packageName, appObject, warning);
				if (!policy.isEmpty()) {
					parsed.put(packageName, policy);
				}
			}
			return new PerAppConfig(parsed);
		} catch (Throwable t) {
			warn(warning, "per_app_config is not valid JSON: " + t);
			return EMPTY;
		}
	}

	private static AppPolicy decodePolicy(String packageName, JSONObject appObject,
			Consumer<String> warning) {
		Override master = Override.INHERIT;
		Object rawMaster = appObject.opt(KEY_MASTER);
		if (rawMaster != null && rawMaster != JSONObject.NULL) {
			if (rawMaster instanceof String) {
				Override parsed = Override.fromKey((String) rawMaster);
				if (parsed == null) {
					warn(warning, "per_app_config has unknown master \""
							+ rawMaster + "\" for " + packageName);
				} else {
					master = parsed;
				}
			} else {
				warn(warning, "per_app_config has a non-string master for " + packageName);
			}
		}

		Map<Feature, Boolean> features = new LinkedHashMap<>();
		JSONObject featuresObject = appObject.optJSONObject(KEY_FEATURES);
		if (featuresObject != null) {
			Iterator<String> featureKeys = featuresObject.keys();
			while (featureKeys.hasNext()) {
				String featureKey = featureKeys.next();
				Feature feature = Feature.fromKey(featureKey);
				if (feature == null || feature == Feature.NONE) {
					warn(warning, "per_app_config has unknown feature \"" + featureKey
							+ "\" for " + packageName);
					continue;
				}
				Object rawValue = featuresObject.opt(featureKey);
				if (rawValue instanceof Boolean) {
					features.put(feature, (Boolean) rawValue);
				} else {
					warn(warning, "per_app_config has a non-boolean value for "
							+ featureKey + " in " + packageName);
				}
			}
		}
		return new AppPolicy(master, features);
	}

	private static void warn(Consumer<String> warning, String message) {
		if (warning != null) {
			warning.accept(message);
		}
	}

	@java.lang.Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PerAppConfig)) {
			return false;
		}
		return apps.equals(((PerAppConfig) other).apps);
	}

	@java.lang.Override
	public int hashCode() {
		return apps.hashCode();
	}

	@java.lang.Override
	public String toString() {
		return "PerAppConfig{apps=" + apps + '}';
	}
}
