/*******************************************************************************
 * Copyright (c) 2004, 2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/

package org.eclipse.buckminster.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.buckminster.runtime.MonitorUtils;
import org.eclipse.buckminster.ui.UiPlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

public abstract class AbstractProjectAction implements IObjectActionDelegate
{
	private IProject[] m_selectedProjects;

	private IWorkbenchPart m_workbenchPart;

	public void setActivePart(IAction action, IWorkbenchPart targetPart)
	{
		m_workbenchPart = targetPart;
	}

	public void run(IAction action)
	{
		final IProject[] projs = this.getSelectedProjects();
		try
		{
			if(this.wantsMonitor())
			{
				final IAction internalAction = action;

				WorkspaceModifyOperation op = new WorkspaceModifyOperation()
				{
					@Override
					protected void execute(IProgressMonitor monitor) throws CoreException
					{
						monitor.beginTask(internalAction.getText(), projs.length * 1000);

						try
						{
							for(IProject proj : projs)
								AbstractProjectAction.this.internalRun(proj, MonitorUtils.subMonitor(monitor, 1000));
						}
						finally
						{
							monitor.done();
						}
					}
				};

				try
				{
					this.getWorkbenchPart().getSite().getWorkbenchWindow().run(true, true, op);
				}
				catch(InterruptedException ie)
				{
					// ignore - it was cancelled
				}
				catch(InvocationTargetException ite)
				{
					Throwable t = ite.getTargetException();
					if(t instanceof CoreException)
					{
						CoreException ce = (CoreException)t;
						ErrorDialog.openError(this.getWorkbenchPart().getSite().getShell(), UiPlugin
								.getResourceString("CreateNewBuckminsterProject.error.title"), null, ce.getStatus());
					}
					else
					{
						String title = UiPlugin.getResourceString("CreateNewBuckminsterProject.error.title");
						String rawMsg = t.getLocalizedMessage();
						if(rawMsg == null)
							rawMsg = t.toString();
						String msg = MessageFormat.format(UiPlugin
								.getResourceString("CreateNewBuckminsterProject.error.msg"), new Object[] { rawMsg });
						MessageDialog.openError(this.getWorkbenchPart().getSite().getShell(), title, msg);
					}
				}
			}
			else
				for(IProject proj : projs)
					this.internalRun(proj, null);
		}
		catch(CoreException ce)
		{
			throw new RuntimeException(ce);
		}
	}

	public void selectionChanged(IAction action, ISelection selection)
	{
		m_selectedProjects = null;
		if(selection == null || selection.isEmpty() || !(selection instanceof IStructuredSelection))
			return;

		List<Object> projects = new ArrayList<Object>();
		IStructuredSelection ss = (IStructuredSelection)selection;
		Iterator<?> iter = ss.iterator();
		while(iter.hasNext())
		{
			Object s = iter.next();
			if(s instanceof IProject)
				projects.add(s);
		}
		m_selectedProjects = projects.toArray(new IProject[0]);
	}

	protected IProject[] getSelectedProjects()
	{
		return m_selectedProjects;
	}

	protected IWorkbenchPart getWorkbenchPart()
	{
		return m_workbenchPart;
	}

	abstract protected void internalRun(IProject proj, IProgressMonitor monitor) throws CoreException;

	protected boolean wantsMonitor()
	{
		return true;
	}
}
