package com.axonivy.utils.cronjob;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.eclipse.core.runtime.IProgressMonitor;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import ch.ivyteam.ivy.persistence.PersistencyException;
import ch.ivyteam.ivy.process.eventstart.AbstractProcessStartEventBean;
import ch.ivyteam.ivy.process.eventstart.IProcessStartEventBeanRuntime;
import ch.ivyteam.ivy.process.eventstart.beans.TimerBean;
import ch.ivyteam.ivy.process.extension.ui.ExtensionUiBuilder;
import ch.ivyteam.ivy.process.extension.ui.IUiFieldEditor;
import ch.ivyteam.ivy.process.extension.ui.UiEditorExtension;
import ch.ivyteam.ivy.service.ServiceException;
import ch.ivyteam.ivy.vars.Variable;
import ch.ivyteam.ivy.vars.Variables;
import ch.ivyteam.log.Logger;

/**
 * Cron Expression Start Event Bean. This bean gets a cron expression via the
 * Configuration string and will schedule by using the expression
 *
 * The Quartz framework is used as underlying scheduler framework.
 * @deprecated use {@link TimerBean} instead
 */
@Deprecated(since="11.2")
public class CronByGlobalVariableTriggerStartEventBean extends AbstractProcessStartEventBean implements Job {
	private Scheduler scheduler = null;
	private JobDetail job = null;
	private CronTrigger trigger = null;
	private String triggerIdentifier;
	private static final String RUNTIME_KEY = "eventRuntime";
	private static final Object SYN_OBJECT = new Object();
	private static Map<String, Long> startedJobs = Collections.synchronizedMap(new HashMap<String, Long>());

	
	public CronByGlobalVariableTriggerStartEventBean() {
		super("CronTrigger", "Description of CronTrigger");
	}

	@Override
	public void initialize(IProcessStartEventBeanRuntime eventRuntime, String configuration) {
		super.initialize(eventRuntime, configuration);
		// Disable Ivy polling
		eventRuntime.setPollTimeInterval(0);

		try {
			Variable var = Variables.of(eventRuntime.getProcessModelVersion().getApplication()).variable(configuration);
			if (var != null) {
				String pattern = var.value();
				SchedulerFactory sf = new StdSchedulerFactory();
				if (pattern != null && pattern.length() > 0) {
					// sf.getScheduler() method has to be called inside synchronized block to prevent racing condition.
					// E.g: two thread initialize Scheduler would cause
					// SchedulerException: Scheduler with name 'DefaultQuartzScheduler' already exists.
					synchronized (SYN_OBJECT) {
						scheduler = sf.getScheduler();
					}
					triggerIdentifier = String.format("cronjobIdentifier:%s", var.name());
					
					job = JobBuilder.newJob(CronByGlobalVariableTriggerStartEventBean.class)
							.withIdentity(triggerIdentifier).build();
					// Pass runtime instance to job, that the job thread has access to it
					job.getJobDataMap().put(RUNTIME_KEY, eventRuntime);
					
					trigger = TriggerBuilder.newTrigger().withIdentity(triggerIdentifier, "Group")
							.withSchedule(CronScheduleBuilder.cronSchedule(pattern)).build();
					
					scheduler.scheduleJob(job, trigger);
					getEventBeanRuntime().getRuntimeLogLogger().info("Init trigger " + triggerIdentifier + " "
							+ trigger.getCronExpression() + " First start: " + trigger.getNextFireTime());
				}
			}
		} catch (SchedulerException | PersistencyException e) {
			scheduler = null;
			getEventBeanRuntime().getRuntimeLogLogger().error(e);
		}
	}

	@Override
	public void start(IProgressMonitor monitor) throws ServiceException {
		super.start(monitor);
		if (scheduler != null && trigger != null) {
			try {
				scheduler.start();
			} catch (SchedulerException e) {
				throw new ServiceException(e);
			}
		}
	}

	@Override
	public void stop(IProgressMonitor monitor) throws ServiceException {
		super.stop(monitor);
		if (scheduler != null) {
			try {
				scheduler.shutdown();
			} catch (SchedulerException e) {
				throw new ServiceException(e);
			}
		}
	}

	@Override
	public void execute(final JobExecutionContext context) throws JobExecutionException {
		if (context.getJobDetail().getJobDataMap().containsKey(RUNTIME_KEY)) {
			final IProcessStartEventBeanRuntime eventRuntime = (IProcessStartEventBeanRuntime) context.getJobDetail()
					.getJobDataMap().get(RUNTIME_KEY);
			
			if (eventRuntime != null) {
				final Logger log = eventRuntime.getRuntimeLogLogger();
				final String triggerIdentifier = context.getTrigger().getJobKey().getName();
				Throwable throwable = null;

				Long jobStartTs = startedJobs.get(triggerIdentifier);
				String nextRun = String.format("Next fire at %1$tF %1$tT.", context.getTrigger().getNextFireTime());
				if (jobStartTs != null) {
					log.warn(
							"Not starting job {0}, since an instance is currently running (the instance is running since {1} ms). {2}",
							triggerIdentifier, System.currentTimeMillis() - jobStartTs, nextRun);
				} else {
					log.info("Starting job {0}", triggerIdentifier);
					long startTs = System.currentTimeMillis();
					startedJobs.put(triggerIdentifier, startTs);
					try {
						eventRuntime.executeAsSystem(new Callable<Void>() {
							@Override
							public Void call() throws Exception {
								String firingReason = "Cron Trigger started " + triggerIdentifier;
								Map<String, Object> parameters = new HashMap<>();
								eventRuntime.fireProcessStartEventRequest(null, firingReason, parameters);
								return null;
							}
						});
					} catch (Throwable t) {
						log.error(
								"Exception while trying to execute Cron Job Utils {0}. Note, the exception might have been shown already.",
								t, triggerIdentifier);
						throwable = t;
					} finally {
						startedJobs.remove(triggerIdentifier);
					}
					
					long endTs = System.currentTimeMillis();
					String stats = String.format("execution time %.3f", (endTs - startTs) / 1000.0);
					if (throwable != null) {
						log.error("Job {0} ended with error {1} ({2})", triggerIdentifier, throwable, stats);
					} else {
						log.info("Job {0} ended normally ({1})", triggerIdentifier, stats);
					}
				}
			}
		}
	}

	
	/**
	 * Editor class to work with the configuration.
	 */
	public static class Editor extends UiEditorExtension {

		private IUiFieldEditor variable;

		
		@Override
		public void initUiFields(ExtensionUiBuilder ui) {
			ui.label("Cron expression defined by the Variable name located in <project>/config/variables.yaml file").create();
			variable = ui.textField().create();
		}

		@Override
		public String getConfiguration() {
			return variable.getText();
		}

		@Override
		public void setConfiguration(String configString) {
			variable.setText(configString);
		}
	}
}
