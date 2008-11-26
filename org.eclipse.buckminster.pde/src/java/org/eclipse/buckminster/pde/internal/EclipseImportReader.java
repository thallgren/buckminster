/*******************************************************************************
 * Copyright (c) 2004, 2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/
package org.eclipse.buckminster.pde.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.buckminster.core.helpers.FileHandle;
import org.eclipse.buckminster.core.helpers.FileUtils;
import org.eclipse.buckminster.core.reader.AbstractRemoteReader;
import org.eclipse.buckminster.core.version.IVersion;
import org.eclipse.buckminster.core.version.ProviderMatch;
import org.eclipse.buckminster.pde.IPDEConstants;
import org.eclipse.buckminster.pde.internal.imports.FeatureImportOperation;
import org.eclipse.buckminster.pde.internal.imports.PluginImportOperation;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.IOUtils;
import org.eclipse.buckminster.runtime.MonitorUtils;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.pde.core.IModel;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.ifeature.IFeatureModel;

/**
 * @author Thomas Hallgren
 */
@SuppressWarnings("restriction")
public class EclipseImportReader extends AbstractRemoteReader implements IPDEConstants
{
	private EclipseImportBase m_base;

	private IModel m_model;

	protected EclipseImportReader(EclipseImportReaderType readerType, ProviderMatch rInfo) throws CoreException
	{
		super(readerType, rInfo);
		m_base = EclipseImportBase.obtain(rInfo.getNodeQuery(), rInfo.getRepositoryURI());

		IVersion version = rInfo.getVersionMatch().getVersion();
		m_model = m_base.isFeature()
				? getFeatureModel(version, new NullProgressMonitor())
				: getPluginModel(version, new NullProgressMonitor());

		if(m_model == null)
			throw BuckminsterException.fromMessage("Unable to load model for %s", m_base.getComponentName());
	}

	public IPluginModelBase getPluginModelBase(String pluginId, String version) throws CoreException
	{
		EclipseImportReaderType rt = (EclipseImportReaderType)getReaderType();
		return rt.getPluginModelBase(m_base.getRemoteLocation(), getConnectContext(), pluginId, version,
				getProviderMatch());
	}

	public void innerMaterialize(IPath destination, IProgressMonitor monitor) throws CoreException
	{
		monitor.beginTask(null, 1000);
		try
		{
			boolean isPlugin = m_model instanceof IPluginModelBase;
			localize(isPlugin, MonitorUtils.subMonitor(monitor, 800));
			IWorkspaceRunnable job = isPlugin
					? getPluginImportJob((IPluginModelBase)m_model, destination)
					: getFeatureImportJob((IFeatureModel)m_model, destination);
			ResourcesPlugin.getWorkspace().run(job, MonitorUtils.subMonitor(monitor, 200));
		}
		finally
		{
			monitor.done();
		}
	}

	@Override
	protected FileHandle innerGetContents(String fileName, IProgressMonitor monitor) throws CoreException, IOException
	{
		monitor.beginTask(null, 1000);

		File destFile = null;
		OutputStream output = null;
		try
		{
			boolean isPlugin = m_model instanceof IPluginModelBase;
			localize(isPlugin, MonitorUtils.subMonitor(monitor, 890));
			destFile = createTempFile();
			output = new FileOutputStream(destFile);
			MonitorUtils.worked(monitor, 10);
			InputStream input = null;
			try
			{
				File source = getInstallLocation();
				if(source.isDirectory())
					input = new FileInputStream(new File(source, fileName));
				else
				{
					ZipFile zipFile = new ZipFile(source);
					ZipEntry entry = zipFile.getEntry(fileName);
					if(entry == null)
						throw new FileNotFoundException(source.getName() + '!' + fileName);
					input = zipFile.getInputStream(entry);
				}
				FileUtils.copyFile(input, output, MonitorUtils.subMonitor(monitor, 100));
			}
			finally
			{
				IOUtils.close(input);
			}
			FileHandle fh = new FileHandle(fileName, destFile, true);
			destFile = null;
			return fh;
		}
		finally
		{
			IOUtils.close(output);
			if(destFile != null)
				destFile.delete();
		}
	}

	IPluginModelBase getPluginModel(IVersion version, IProgressMonitor monitor) throws CoreException
	{
		monitor.beginTask(null, m_base.isLocal()
				? 1000
				: 2000);
		monitor.subTask("Downloading " + m_base.getComponentName());
		try
		{
			EclipseImportReaderType readerType = (EclipseImportReaderType)getReaderType();
			if(!m_base.isLocal())
				localize(true, MonitorUtils.subMonitor(monitor, 1000));

			IPluginModelBase model = null;
			for(IPluginModelBase candidate : m_base.getPluginModels(readerType, MonitorUtils.subMonitor(monitor, 1000)))
			{
				if(version == null
						|| version.toString().equals(candidate.getBundleDescription().getVersion().toString()))
				{
					model = candidate;
					break;
				}
			}
			return model;
		}
		finally
		{
			monitor.done();
		}
	}

	private IWorkspaceRunnable getFeatureImportJob(IFeatureModel model, IPath destination)
	{
		return new FeatureImportOperation((EclipseImportReaderType)getReaderType(), model, getNodeQuery(), destination,
				m_base.getType() == PluginImportOperation.IMPORT_BINARY);
	}

	private IFeatureModel getFeatureModel(IVersion version, IProgressMonitor monitor) throws CoreException
	{
		IFeatureModel model = null;
		monitor.beginTask(null, m_base.isLocal()
				? 1000
				: 3000);
		try
		{
			if(!m_base.isLocal())
				localize(false, MonitorUtils.subMonitor(monitor, 1000));

			EclipseImportReaderType readerType = (EclipseImportReaderType)getReaderType();
			for(IFeatureModel candidate : m_base.getFeatureModels(readerType, MonitorUtils.subMonitor(monitor, 1000)))
			{
				if(version == null || version.toString().equals(candidate.getFeature().getVersion()))
				{
					model = candidate;
					break;
				}
			}
			return model;
		}
		finally
		{
			monitor.done();
		}
	}

	private File getInstallLocation()
	{
		String location = (m_model instanceof IPluginModelBase)
				? ((IPluginModelBase)m_model).getInstallLocation()
				: ((IFeatureModel)m_model).getInstallLocation();

		return new File(location);
	}

	private IWorkspaceRunnable getPluginImportJob(IPluginModelBase model, IPath destination)
	{
		PluginImportOperation job = new PluginImportOperation(model, getNodeQuery(), destination, m_base.getType());
		job.setClasspathCollector((EclipseImportReaderType)getReaderType());
		return job;
	}

	private void localize(boolean isPlugin, IProgressMonitor monitor) throws CoreException
	{
		if(m_base.isLocal())
		{
			MonitorUtils.complete(monitor);
			return;
		}

		monitor.beginTask(null, 1000);
		ProviderMatch ri = getProviderMatch();
		m_base = ((EclipseImportReaderType)getReaderType()).localizeContents(ri, isPlugin, MonitorUtils.subMonitor(
				monitor, 950));

		// Model is now local, so reset it.
		//
		IVersion version = ri.getVersionMatch().getVersion();
		IProgressMonitor subMon = MonitorUtils.subMonitor(monitor, 50);
		m_model = isPlugin
				? getPluginModel(version, subMon)
				: getFeatureModel(version, subMon);
		if(m_model == null)
			throw BuckminsterException.fromMessage("Unable to load localized model for %s", m_base.getComponentName());
		monitor.done();
	}
}
