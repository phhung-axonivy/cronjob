package com.axonivy.utils.automatedprocessstartutils.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.axonivy.utils.automatedprocessstartutils.demo.BusinessCaseDemo;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.environment.IvyTest;
import ch.ivyteam.ivy.scripting.objects.DateTime;

/**
 * This UnitTest is testing the demo process.
 */
@IvyTest
public class CronByGlobalVariableTriggerStartEventBeanIvyTest {

	@Test
	public void testCronJongStarted() throws Exception {
		TimeUnit.SECONDS.sleep(5);
		BusinessCaseDemo bCaseDemo = Ivy.repo().get(BusinessCaseDemo.class);
		assertThat(bCaseDemo).isNotNull();
		assertThat(bCaseDemo.getStartTime()).as("Cron job did not started").isNotNull();
		assertThat(bCaseDemo.getStartTime()).as("Cron job did not started in the past", bCaseDemo.getStartTime()).isLessThan(new DateTime());
	}

}