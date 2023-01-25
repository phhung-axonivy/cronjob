package com.axonivy.utils.automatedprocessstartutils.test;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.environment.IvyTest;

/**
 * This UnitTest is testing the demo process.
 */
@IvyTest
public class CronByGlobalVariableTriggerStartEventBeanIvyTest {

	@Test
	public void afterPropertiesSetJobTestJobDetailAndTriggerNotNull() throws Exception {
		TimeUnit.SECONDS.sleep(1);
		Ivy.datacache().getSessionCache().getEntry("com.axonivy.utils.automatedprocessstartutils", "demoStartTime")
				.isValid();
	}

}