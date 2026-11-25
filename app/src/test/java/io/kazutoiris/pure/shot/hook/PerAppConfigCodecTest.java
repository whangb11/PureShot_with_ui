package io.kazutoiris.pure.shot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class PerAppConfigCodecTest {

	private static final String APP = "com.example.app";

	@Test
	public void emptyConfigRoundTrips() {
		PerAppConfig decoded = PerAppConfig.decode(PerAppConfig.EMPTY.encode());
		assertTrue(decoded.isEmpty());
		assertEquals(PerAppConfig.EMPTY, decoded);
	}

	@Test
	public void roundTripKeepsExplicitOverrides() {
		AppPolicy policy = new AppPolicy(Override.OFF, features(
				Feature.STATUS_BAR, true,
				Feature.TOAST, false));
		PerAppConfig config = PerAppConfig.EMPTY.withPolicy(APP, policy);

		PerAppConfig decoded = PerAppConfig.decode(config.encode());

		assertEquals(policy, decoded.get(APP));
		assertEquals(1, decoded.getApps().size());
	}

	@Test
	public void encodeOmitsInheritValuesAndEmptyApps() throws Exception {
		AppPolicy inherited = new AppPolicy(Override.INHERIT,
				features(Feature.STATUS_BAR, true));
		PerAppConfig config = PerAppConfig.EMPTY
				.withPolicy("com.example.inherit", inherited)
				.withPolicy("com.example.empty", AppPolicy.DEFAULT);

		JSONObject root = new JSONObject(config.encode());
		JSONObject apps = root.getJSONObject("apps");

		assertEquals(1, apps.length());
		JSONObject encoded = apps.getJSONObject("com.example.inherit");
		assertFalse(encoded.has("master"));
		assertTrue(encoded.getJSONObject("features").getBoolean("status_bar"));
	}

	@Test
	public void encodeUsesStableFeatureKeys() {
		AppPolicy policy = new AppPolicy(Override.INHERIT,
				features(Feature.NAVIGATION_BAR, false));
		String json = PerAppConfig.EMPTY.withPolicy(APP, policy).encode();

		assertTrue(json.contains("\"navigation_bar\""));
		assertFalse(json.contains("NAVIGATION_BAR"));
	}

	@Test
	public void decodeIgnoresUnknownFeatureKeys() {
		String json = "{\"version\":1,\"apps\":{\"" + APP + "\":{\"features\":{"
				+ "\"status_bar\":true,\"made_up\":true}}}}";

		PerAppConfig decoded = PerAppConfig.decode(json);

		assertEquals(Boolean.TRUE, decoded.get(APP).getFeatureOverride(Feature.STATUS_BAR));
		assertEquals(1, decoded.get(APP).getFeatures().size());
	}

	@Test
	public void decodeIgnoresUnknownMasterValues() {
		String json = "{\"version\":1,\"apps\":{\"" + APP + "\":{"
				+ "\"master\":\"sometimes\",\"features\":{\"toast\":false}}}}";

		PerAppConfig decoded = PerAppConfig.decode(json);

		assertEquals(Override.INHERIT, decoded.get(APP).getMaster());
		assertEquals(Boolean.FALSE, decoded.get(APP).getFeatureOverride(Feature.TOAST));
	}

	@Test
	public void decodeIgnoresNonBooleanFeatureValues() {
		String json = "{\"version\":1,\"apps\":{\"" + APP + "\":{\"features\":{"
				+ "\"toast\":\"yes\",\"overlay\":1,\"other\":true}}}}";

		PerAppConfig decoded = PerAppConfig.decode(json);

		assertNull(decoded.get(APP).getFeatureOverride(Feature.TOAST));
		assertNull(decoded.get(APP).getFeatureOverride(Feature.OVERLAY));
		assertEquals(Boolean.TRUE, decoded.get(APP).getFeatureOverride(Feature.OTHER));
	}

	@Test
	public void invalidJsonReturnsEmpty() {
		assertTrue(PerAppConfig.decode("{not json").isEmpty());
		assertTrue(PerAppConfig.decode("null").isEmpty());
		assertTrue(PerAppConfig.decode("[]").isEmpty());
	}

	@Test
	public void futureVersionIsRejected() {
		assertTrue(PerAppConfig.decode(
				"{\"version\":2,\"apps\":{\"" + APP + "\":{\"master\":\"off\"}}}").isEmpty());
	}

	@Test
	public void missingVersionIsAccepted() {
		PerAppConfig decoded = PerAppConfig.decode(
				"{\"apps\":{\"" + APP + "\":{\"master\":\"off\"}}}");
		assertEquals(Override.OFF, decoded.get(APP).getMaster());
	}

	@Test
	public void oversizedPayloadIsRejected() {
		String oversized = new String(new char[PerAppConfig.MAX_JSON_LENGTH + 1])
				.replace('\0', 'x');

		assertTrue(PerAppConfig.decode(oversized).isEmpty());
	}

	@Test
	public void brokenAppEntriesAreSkipped() {
		String json = "{\"version\":1,\"apps\":{"
				+ "\"com.example.bad\":42,"
				+ "\"com.example.good\":{\"master\":\"on\"}}}";

		PerAppConfig decoded = PerAppConfig.decode(json);

		assertEquals(1, decoded.getApps().size());
		assertEquals(Override.ON, decoded.get("com.example.good").getMaster());
	}

	@Test
	public void unknownTopLevelKeysAreIgnored() {
		PerAppConfig decoded = PerAppConfig.decode(
				"{\"version\":1,\"extra\":true,\"apps\":{}}");
		assertTrue(decoded.isEmpty());
	}

	@Test
	public void warningsAreReportedForIgnoredEntries() {
		List<String> warnings = new ArrayList<>();
		PerAppConfig.decode("{\"version\":1,\"apps\":{\"" + APP + "\":{"
				+ "\"features\":{\"made_up\":true,\"toast\":\"yes\"}}}}", warnings::add);

		assertEquals(2, warnings.size());
		assertTrue(warnings.stream().anyMatch(message -> message.contains("made_up")));
		assertTrue(warnings.stream().anyMatch(message -> message.contains("toast")));
	}

	@Test
	public void allFeaturesSurviveRoundTrip() {
		EnumMap<Feature, Boolean> overrides = new EnumMap<>(Feature.class);
		for (Feature feature : Feature.configurable()) {
			overrides.put(feature, feature.ordinal() % 2 == 0);
		}
		AppPolicy policy = new AppPolicy(Override.ON, overrides);

		PerAppConfig decoded = PerAppConfig.decode(
				PerAppConfig.EMPTY.withPolicy(APP, policy).encode());

		assertEquals(policy, decoded.get(APP));
		assertEquals(Feature.configurable().size(),
				decoded.get(APP).getFeatures().size());
	}

	@Test
	public void malformedPolicyCanBeClearedFromConfig() {
		PerAppConfig config = new PerAppConfig(Collections.singletonMap(
				APP, new AppPolicy(Override.OFF, Collections.emptyMap())));

		assertTrue(config.without(APP).isEmpty());
		assertTrue(config.withPolicy(APP, AppPolicy.DEFAULT).isEmpty());
	}

	private static Map<Feature, Boolean> features(Object... pairs) {
		EnumMap<Feature, Boolean> result = new EnumMap<>(Feature.class);
		for (int i = 0; i < pairs.length; i += 2) {
			result.put((Feature) pairs[i], (Boolean) pairs[i + 1]);
		}
		return result;
	}
}
