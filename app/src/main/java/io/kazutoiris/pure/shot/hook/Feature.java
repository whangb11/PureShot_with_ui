package io.kazutoiris.pure.shot.hook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Independently switchable groups of system windows that PureShot can hide.
 */
public enum Feature {
	STATUS_BAR("status_bar"),
	NAVIGATION_BAR("navigation_bar"),
	INPUT_METHOD("input_method"),
	TOAST("toast"),
	OVERLAY("overlay"),
	OTHER("other"),
	/** Not a real feature; used for windows that PureShot never touches. */
	NONE("none");

	private static final List<Feature> CONFIGURABLE;

	static {
		List<Feature> features = new ArrayList<>();
		for (Feature feature : values()) {
			if (feature != NONE) {
				features.add(feature);
			}
		}
		CONFIGURABLE = Collections.unmodifiableList(features);
	}

	private final String key;

	Feature(String key) {
		this.key = key;
	}

	/**
	 * Stable serialization key. Never persist {@link #name()}: R8 may rename enum constants.
	 */
	public String getKey() {
		return key;
	}

	/** Features that have a global switch and can be overridden per app. */
	public static List<Feature> configurable() {
		return CONFIGURABLE;
	}

	public static Feature fromKey(String key) {
		if (key == null) {
			return null;
		}
		for (Feature feature : values()) {
			if (feature.key.equals(key)) {
				return feature;
			}
		}
		return null;
	}
}
