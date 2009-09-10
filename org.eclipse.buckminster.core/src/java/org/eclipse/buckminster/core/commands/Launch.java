/*******************************************************************************
 * Copyright (c) 2009, eXXcellent solutions gmbh
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *
 * Contributors:
 *     Achim Demelt - initial API and implementation
 *******************************************************************************/
package org.eclipse.buckminster.core.commands;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.buckminster.cmdline.Option;
import org.eclipse.buckminster.cmdline.OptionDescriptor;
import org.eclipse.buckminster.cmdline.OptionValueType;
import org.eclipse.buckminster.cmdline.UsageException;
import org.eclipse.buckminster.core.CorePlugin;
import org.eclipse.buckminster.core.Messages;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.osgi.util.NLS;

public class Launch extends WorkspaceCommand
{
	private final class StreamListener implements IStreamListener
	{
		private PrintStream stream;

		public StreamListener(IStreamMonitor monitor, String outputFile, boolean stdout)
		{
			Launch.this.m_listeners.add(this);

			// open stream or use stdout/stderr
			if("-".equals(outputFile)) //$NON-NLS-1$
				this.stream = stdout
						? Logger.getOutStream()
						: Logger.getErrStream();
			else
				try
				{
					this.stream = new PrintStream(outputFile);
				}
				catch(FileNotFoundException e)
				{
					CorePlugin.getLogger().error(e, Messages.Launch_Cannot_open_stream, outputFile);
					this.stream = stdout
							? Logger.getOutStream()
							: Logger.getErrStream();
				}

			// dump initial contents
			stream.print(monitor.getContents());
			// and now register to be notified of subsequent events
			monitor.addListener(this);
		}

		public void close()
		{
			if(stream != Logger.getOutStream() && stream != Logger.getErrStream())
				stream.close();
		}

		public void streamAppended(String text, IStreamMonitor monitor)
		{
			stream.print(text);
		}
	}

	private static final OptionDescriptor LAUNCH_DESCRIPTOR = new OptionDescriptor('l', "launch", //$NON-NLS-1$
			OptionValueType.REQUIRED);

	private static final OptionDescriptor STDOUT_DESCRIPTOR = new OptionDescriptor(null, "stdout", //$NON-NLS-1$
			OptionValueType.OPTIONAL);

	private static final OptionDescriptor STDERR_DESCRIPTOR = new OptionDescriptor(null, "stderr", //$NON-NLS-1$
			OptionValueType.OPTIONAL);

	private String m_launchName;

	private String m_stdOutFile;

	private String m_stdErrFile;

	private IStreamMonitor[] m_stdOut;

	private IStreamMonitor[] m_stdErr;

	private List<StreamListener> m_listeners = new ArrayList<StreamListener>();

	/**
	 * Returns the content of the standard error streams of all processes launch by the configuration.
	 * 
	 * @return The contents of all standard error streams. An empty string if no processes were launched or no content
	 *         was produced.
	 */
	public String getStdErr()
	{
		StringBuffer content = new StringBuffer();
		for(IStreamMonitor err : m_stdErr)
		{
			content.append(err.getContents());
		}
		return content.toString();
	}

	/**
	 * Returns the content of the standard output streams of all processes launch by the configuration.
	 * 
	 * @return The contents of all standard output streams. An empty string if no processes were launched or no content
	 *         was produced.
	 */
	public String getStdOut()
	{
		StringBuffer content = new StringBuffer();
		for(IStreamMonitor out : m_stdOut)
		{
			content.append(out.getContents());
		}
		return content.toString();
	}

	/**
	 * Returns the launch mode that is used for launching. Defaults to {@link ILaunchManager#RUN_MODE}. Subclasses may
	 * override this to launch in other modes.
	 * 
	 * @return The launch mode to use. Never null.
	 */
	protected String getLaunchMode()
	{
		return ILaunchManager.RUN_MODE;
	}

	@Override
	protected void getOptionDescriptors(List<OptionDescriptor> appendHere) throws Exception
	{
		super.getOptionDescriptors(appendHere);
		appendHere.add(LAUNCH_DESCRIPTOR);
		appendHere.add(STDOUT_DESCRIPTOR);
		appendHere.add(STDERR_DESCRIPTOR);
	}

	@Override
	protected void handleOption(Option option) throws Exception
	{
		super.handleOption(option);

		if(option.is(LAUNCH_DESCRIPTOR))
			m_launchName = option.getValue();
		else if(option.is(STDOUT_DESCRIPTOR))
			m_stdOutFile = option.getValue() == null
					? "-" //$NON-NLS-1$
					: option.getValue();
		else if(option.is(STDERR_DESCRIPTOR))
			m_stdErrFile = option.getValue() == null
					? "-" //$NON-NLS-1$
					: option.getValue();
	}

	@Override
	protected int internalRun(IProgressMonitor monitor) throws Exception
	{
		if(m_launchName == null)
			throw new UsageException(Messages.Launch_No_launch_config);

		IResource launchFile = ResourcesPlugin.getWorkspace().getRoot().findMember(m_launchName);
		if(launchFile == null || launchFile.getType() != IResource.FILE || !launchFile.exists())
			throw BuckminsterException.fromMessage(NLS.bind(Messages.Launch_Cannot_open_launch_config, m_launchName));

		ILaunchConfiguration launchConfiguration = DebugPlugin.getDefault().getLaunchManager().getLaunchConfiguration(
				(IFile)launchFile);
		ILaunch launch = launchConfiguration.launch(getLaunchMode(), monitor);

		// capture stdout/stderr streams
		IProcess[] processes = launch.getProcesses();
		m_stdOut = new IStreamMonitor[processes.length];
		m_stdErr = new IStreamMonitor[processes.length];
		for(int i = 0; i < processes.length; i++)
		{
			m_stdOut[i] = processes[i].getStreamsProxy().getOutputStreamMonitor();
			if(m_stdOutFile != null)
				new StreamListener(m_stdOut[i], m_stdOutFile, true);
			m_stdErr[i] = processes[i].getStreamsProxy().getErrorStreamMonitor();
			if(m_stdErrFile != null)
				new StreamListener(m_stdErr[i], m_stdErrFile, false);
		}

		try
		{
			// TODO: wait for a configurable, finite time and terminate process if overdue
			while(!launch.isTerminated())
				Thread.sleep(500);

			// check for process exit status
			int result = 0;
			for(IProcess p : processes)
				if(p.getExitValue() != 0)
				{
					CorePlugin.getLogger().warning(Messages.Launch_Terminated_with_exit_status, p.getLabel(),
							Integer.valueOf(p.getExitValue()));
					result = p.getExitValue();
				}

			return result;
		}
		finally
		{
			for(StreamListener listener : m_listeners)
				listener.close();
		}
	}
}