package io.kazutoiris.pure.shot.hook;

/**
 * Tri-state per-app override: follow the global setting, or force a value.
 */
public enum Override {
	INHERIT("inherit"),
	ON("on"),
	OFF("off");

	private final String key;

	Override(String key) {
		this.key = key;
	}

	/**
	 * Stable serialization key. Never persist {@link #name()}: R8 may rename enum constants.
	 */
	public String getKey() {
		return key;
	}

	public static Override fromKey(String key) {
		if (key == null) {
			return null;
		}
		for (Override value : values()) {
			if (value.key.equals(key)) {
				return value;
			}
		}
		return null;
	}
}
