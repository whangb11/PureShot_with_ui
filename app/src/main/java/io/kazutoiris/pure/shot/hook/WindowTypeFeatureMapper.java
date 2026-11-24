package io.kazutoiris.pure.shot.hook;

import android.view.WindowManager;

/**
 * Maps a WindowManager window type to the user-facing feature group that controls it.
 *
 * <p>Many system window types (e.g. {@code TYPE_STATUS_BAR_PANEL}) are hidden from the public
 * SDK, so they are written as literals. The values are stable AOSP constants relative to
 * {@link WindowManager.LayoutParams#FIRST_SYSTEM_WINDOW} (2000) and are inlined by the compiler,
 * which also keeps this class usable from plain JVM unit tests.
 */
public final class WindowTypeFeatureMapper {

	private static final int TYPE_STATUS_BAR_PANEL = 2014; // FIRST_SYSTEM_WINDOW + 14
	private static final int TYPE_STATUS_BAR_SUB_PANEL = 2017; // FIRST_SYSTEM_WINDOW + 17
	private static final int TYPE_NAVIGATION_BAR = 2019; // FIRST_SYSTEM_WINDOW + 19
	private static final int TYPE_NAVIGATION_BAR_PANEL = 2024; // FIRST_SYSTEM_WINDOW + 24
	private static final int TYPE_SECURE_SYSTEM_OVERLAY = 2015; // FIRST_SYSTEM_WINDOW + 15
	private static final int TYPE_DISPLAY_OVERLAY = 2026; // FIRST_SYSTEM_WINDOW + 26
	private static final int TYPE_NOTIFICATION_SHADE = 2040; // FIRST_SYSTEM_WINDOW + 40
	private static final int TYPE_STATUS_BAR_ADDITIONAL = 2041; // FIRST_SYSTEM_WINDOW + 41

	private WindowTypeFeatureMapper() {
	}

	public static Feature map(int windowType) {
		if (windowType < WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW) {
			return Feature.NONE;
		}
		if (windowType == WindowManager.LayoutParams.TYPE_WALLPAPER) {
			return Feature.NONE;
		}

		switch (windowType) {
			case WindowManager.LayoutParams.TYPE_STATUS_BAR: // 2000
			case TYPE_STATUS_BAR_PANEL:
			case TYPE_STATUS_BAR_SUB_PANEL:
			case TYPE_NOTIFICATION_SHADE:
			case TYPE_STATUS_BAR_ADDITIONAL:
				return Feature.STATUS_BAR;

			case TYPE_NAVIGATION_BAR:
			case TYPE_NAVIGATION_BAR_PANEL:
				return Feature.NAVIGATION_BAR;

			case WindowManager.LayoutParams.TYPE_INPUT_METHOD: // 2011
			case WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG: // 2012
				return Feature.INPUT_METHOD;

			case WindowManager.LayoutParams.TYPE_TOAST: // 2005
				return Feature.TOAST;

			case WindowManager.LayoutParams.TYPE_PHONE: // 2002
			case WindowManager.LayoutParams.TYPE_SYSTEM_ALERT: // 2003
			case WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY: // 2006
			case WindowManager.LayoutParams.TYPE_PRIORITY_PHONE: // 2007
			case WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG: // 2008
			case WindowManager.LayoutParams.TYPE_SYSTEM_ERROR: // 2010
			case TYPE_SECURE_SYSTEM_OVERLAY:
			case TYPE_DISPLAY_OVERLAY:
			case WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY: // 2032
			case WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY: // 2038
				return Feature.OVERLAY;

			default:
				return Feature.OTHER;
		}
	}
}
