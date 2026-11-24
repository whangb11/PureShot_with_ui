package io.kazutoiris.pure.shot.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class WindowTypeFeatureMapperTest {

	@Test
	public void mapsStatusBarTypes() {
		assertFeature(Feature.STATUS_BAR, 2000, 2014, 2017, 2040, 2041);
	}

	@Test
	public void mapsNavigationBarTypes() {
		assertFeature(Feature.NAVIGATION_BAR, 2019, 2024);
	}

	@Test
	public void mapsInputMethodTypes() {
		assertFeature(Feature.INPUT_METHOD, 2011, 2012);
	}

	@Test
	public void mapsToastType() {
		assertFeature(Feature.TOAST, 2005);
	}

	@Test
	public void mapsOverlayTypes() {
		assertFeature(Feature.OVERLAY,
				2002, 2003, 2006, 2007, 2008, 2010, 2015, 2026, 2032, 2038);
	}

	@Test
	public void mapsRemainingSystemTypesToOther() {
		assertFeature(Feature.OTHER,
				2016, 2018, 2020, 2021, 2022, 2027, 2030, 2036, 2037, 2999);
	}

	@Test
	public void neverMapsApplicationWindows() {
		assertFeature(Feature.NONE, -1, 0, 1, 1000, 1999);
	}

	@Test
	public void neverMapsWallpaper() {
		assertFeature(Feature.NONE, 2013);
	}

	@Test
	public void everySystemWindowTypeIsCovered() {
		for (int windowType = 2000; windowType <= 2400; windowType++) {
			Feature feature = WindowTypeFeatureMapper.map(windowType);
			if (windowType == 2013) {
				assertEquals(Feature.NONE, feature);
			} else {
				assertNotEquals("windowType " + windowType + " must not be NONE", Feature.NONE, feature);
			}
		}
	}

	private static void assertFeature(Feature expected, int... windowTypes) {
		for (int windowType : windowTypes) {
			assertEquals("windowType " + windowType, expected, WindowTypeFeatureMapper.map(windowType));
		}
	}
}
