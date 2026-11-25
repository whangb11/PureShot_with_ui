package io.kazutoiris.pure.shot.hook;

import java.util.Map;

/**
 * Pure per-app policy evaluation. No Android dependencies, so the full truth table is unit tested
 * on the JVM and the same code runs inside system_server.
 *
 * <pre>
 * effective(feature, packageName):
 *   1. unknown/NONE feature       -&gt; false
 *   2. !moduleEnabled             -&gt; false
 *   3. no app policy              -&gt; global feature
 *   4. app.master == OFF          -&gt; false
 *   5. app feature override       -&gt; override value
 *   6. otherwise                  -&gt; global feature
 * </pre>
 */
public final class PolicyResolver {

	private PolicyResolver() {
	}

	/** Global switch state needed to resolve a feature for one package. */
	public interface Globals {
		boolean isModuleEnabled();

		boolean isFeatureEnabled(Feature feature);
	}

	public static boolean isEnabled(Globals globals, Map<String, AppPolicy> perApp,
			Feature feature, String packageName) {
		if (feature == null || feature == Feature.NONE) {
			return false;
		}
		if (globals == null || !globals.isModuleEnabled()) {
			return false;
		}

		AppPolicy policy = perApp != null && packageName != null
				? perApp.get(packageName) : null;
		if (policy == null) {
			return globals.isFeatureEnabled(feature);
		}
		if (policy.getMaster() == Override.OFF) {
			return false;
		}
		Boolean override = policy.getFeatureOverride(feature);
		if (override != null) {
			return override;
		}
		return globals.isFeatureEnabled(feature);
	}
}
