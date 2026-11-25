package io.kazutoiris.pure.shot.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.kazutoiris.pure.shot.hook.PolicyResolver.Globals;

import org.junit.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class PolicyResolverTest {

	private static final String APP = "com.example.app";
	private static final String OTHER_APP = "com.example.other";
	private static final String SYSTEM_UI = "com.android.systemui";

	@Test
	public void globalModuleSwitchAlwaysWins() {
		Globals globals = globals(false, true);
		assertFalse(PolicyResolver.isEnabled(
				globals, Collections.emptyMap(), Feature.STATUS_BAR, APP));
		// Even an explicit per-app override cannot bypass the hard master switch.
		assertFalse(PolicyResolver.isEnabled(
				globals, apps(policy(Override.ON, Feature.STATUS_BAR, true)),
				Feature.STATUS_BAR, APP));
	}

	@Test
	public void unconfiguredAppInheritsGlobalFeature() {
		assertTrue(PolicyResolver.isEnabled(
				globals(true, true), Collections.emptyMap(), Feature.STATUS_BAR, APP));
		assertFalse(PolicyResolver.isEnabled(
				globals(true, false), Collections.emptyMap(), Feature.STATUS_BAR, APP));
	}

	@Test
	public void unknownPackageInheritsGlobalFeature() {
		Map<String, AppPolicy> perApp = apps(policy(Override.OFF, null, null));
		assertTrue(PolicyResolver.isEnabled(
				globals(true, true), perApp, Feature.STATUS_BAR, OTHER_APP));
	}

	@Test
	public void nullPackageInheritsGlobalFeature() {
		assertTrue(PolicyResolver.isEnabled(
				globals(true, true), Collections.emptyMap(), Feature.NAVIGATION_BAR, null));
	}

	@Test
	public void appMasterOffDisablesEveryFeature() {
		Map<String, AppPolicy> perApp = apps(policy(Override.OFF, Feature.STATUS_BAR, true));
		assertFalse(PolicyResolver.isEnabled(
				globals(true, true), perApp, Feature.STATUS_BAR, APP));
		assertFalse(PolicyResolver.isEnabled(
				globals(true, true), perApp, Feature.NAVIGATION_BAR, APP));
	}

	@Test
	public void appMasterOnDoesNotForceFeaturesOn() {
		Map<String, AppPolicy> perApp = apps(policy(Override.ON, null, null));
		assertTrue(PolicyResolver.isEnabled(
				globals(true, true), perApp, Feature.STATUS_BAR, APP));
		assertFalse(PolicyResolver.isEnabled(
				globals(true, false), perApp, Feature.STATUS_BAR, APP));
	}

	@Test
	public void featureOverrideCanTurnGlobalOffIntoOn() {
		Map<String, AppPolicy> perApp = apps(
				policy(Override.INHERIT, Feature.NAVIGATION_BAR, true));
		assertTrue(PolicyResolver.isEnabled(
				globals(true, false), perApp, Feature.NAVIGATION_BAR, APP));
	}

	@Test
	public void featureOverrideCanTurnGlobalOnIntoOff() {
		Map<String, AppPolicy> perApp = apps(
				policy(Override.INHERIT, Feature.NAVIGATION_BAR, false));
		assertFalse(PolicyResolver.isEnabled(
				globals(true, true), perApp, Feature.NAVIGATION_BAR, APP));
		// Other features keep following the global switch.
		assertTrue(PolicyResolver.isEnabled(
				globals(true, true), perApp, Feature.STATUS_BAR, APP));
	}

	@Test
	public void featureOverrideIsStillBlockedByAppMasterOff() {
		AppPolicy policy = new AppPolicy(Override.OFF,
				features(Feature.NAVIGATION_BAR, true));
		assertFalse(PolicyResolver.isEnabled(
				globals(true, true), apps(policy), Feature.NAVIGATION_BAR, APP));
	}

	@Test
	public void systemUiFallbackUsesGlobalWhenUnconfigured() {
		assertTrue(PolicyResolver.isEnabled(
				globals(true, true), Collections.emptyMap(), Feature.STATUS_BAR, SYSTEM_UI));
		assertFalse(PolicyResolver.isEnabled(
				globals(true, false), Collections.emptyMap(), Feature.STATUS_BAR, SYSTEM_UI));
	}

	@Test
	public void systemUiFallbackUsesItsOwnAppPolicy() {
		Map<String, AppPolicy> perApp = new LinkedHashMap<>();
		perApp.put(SYSTEM_UI, policy(Override.OFF, null, null));
		assertFalse(PolicyResolver.isEnabled(
				globals(true, true), perApp, Feature.STATUS_BAR, SYSTEM_UI));
	}

	@Test
	public void noneFeatureIsAlwaysDisabled() {
		assertFalse(PolicyResolver.isEnabled(
				globals(true, true),
				apps(policy(Override.ON, Feature.STATUS_BAR, true)),
				Feature.NONE, APP));
	}

	@Test
	public void nullFeatureIsAlwaysDisabled() {
		assertFalse(PolicyResolver.isEnabled(
				globals(true, true), Collections.emptyMap(), null, APP));
	}

	private static Globals globals(boolean moduleEnabled, boolean featureEnabled) {
		return new Globals() {
			@java.lang.Override
			public boolean isModuleEnabled() {
				return moduleEnabled;
			}

			@java.lang.Override
			public boolean isFeatureEnabled(Feature feature) {
				return featureEnabled;
			}
		};
	}

	private static Map<String, AppPolicy> apps(AppPolicy policy) {
		Map<String, AppPolicy> perApp = new LinkedHashMap<>();
		perApp.put(APP, policy);
		return perApp;
	}

	private static AppPolicy policy(Override master, Feature feature, Boolean value) {
		return new AppPolicy(master, feature != null
				? features(feature, value) : Collections.emptyMap());
	}

	private static Map<Feature, Boolean> features(Object... pairs) {
		EnumMap<Feature, Boolean> result = new EnumMap<>(Feature.class);
		for (int i = 0; i < pairs.length; i += 2) {
			result.put((Feature) pairs[i], (Boolean) pairs[i + 1]);
		}
		return result;
	}
}
