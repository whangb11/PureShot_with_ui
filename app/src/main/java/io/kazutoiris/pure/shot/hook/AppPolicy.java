package io.kazutoiris.pure.shot.hook;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration for a single application.
 *
 * <p>{@link #getMaster()} controls whether the app participates at all. A {@link Override#ON}
 * master does not force features on: it only lifts an application-level disable. Feature values
 * override the global switch in both directions when present.
 */
public final class AppPolicy {

	public static final AppPolicy DEFAULT =
			new AppPolicy(Override.INHERIT, Collections.emptyMap());

	private final Override master;
	private final Map<Feature, Boolean> features;

	public AppPolicy(Override master, Map<Feature, Boolean> features) {
		this.master = master != null ? master : Override.INHERIT;
		EnumMap<Feature, Boolean> copy = new EnumMap<>(Feature.class);
		if (features != null) {
			for (Map.Entry<Feature, Boolean> entry : features.entrySet()) {
				Feature feature = entry.getKey();
				Boolean value = entry.getValue();
				if (feature != null && feature != Feature.NONE && value != null) {
					copy.put(feature, value);
				}
			}
		}
		this.features = Collections.unmodifiableMap(copy);
	}

	public Override getMaster() {
		return master;
	}

	public Map<Feature, Boolean> getFeatures() {
		return features;
	}

	/** Returns the explicit override, or {@code null} when the app inherits the global value. */
	public Boolean getFeatureOverride(Feature feature) {
		return feature != null ? features.get(feature) : null;
	}

	public AppPolicy withMaster(Override newMaster) {
		Override normalized = newMaster != null ? newMaster : Override.INHERIT;
		if (normalized == master) {
			return this;
		}
		return new AppPolicy(normalized, features);
	}

	/** Pass {@code null} to remove the override and inherit the global value again. */
	public AppPolicy withFeature(Feature feature, Boolean value) {
		if (feature == null || feature == Feature.NONE) {
			return this;
		}
		Boolean current = features.get(feature);
		if (Objects.equals(current, value)) {
			return this;
		}
		EnumMap<Feature, Boolean> copy = new EnumMap<>(Feature.class);
		copy.putAll(features);
		if (value == null) {
			copy.remove(feature);
		} else {
			copy.put(feature, value);
		}
		return new AppPolicy(master, copy);
	}

	public boolean isEmpty() {
		return master == Override.INHERIT && features.isEmpty();
	}

	@java.lang.Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AppPolicy)) {
			return false;
		}
		AppPolicy policy = (AppPolicy) other;
		return master == policy.master && features.equals(policy.features);
	}

	@java.lang.Override
	public int hashCode() {
		return Objects.hash(master, features);
	}

	@java.lang.Override
	public String toString() {
		return "AppPolicy{master=" + master + ", features=" + features + '}';
	}
}
