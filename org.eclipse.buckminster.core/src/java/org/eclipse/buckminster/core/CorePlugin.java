/*******************************************************************************
 * Copyright (c) 2004, 2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/

package org.eclipse.buckminster.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;

import org.eclipse.buckminster.core.actor.IPerformManager;
import org.eclipse.buckminster.core.ctype.IComponentType;
import org.eclipse.buckminster.core.ctype.IResolutionBuilder;
import org.eclipse.buckminster.core.ctype.MissingBuilderException;
import org.eclipse.buckminster.core.ctype.MissingComponentTypeException;
import org.eclipse.buckminster.core.helpers.ShortDurationURLCache;
import org.eclipse.buckminster.core.internal.actor.PerformManager;
import org.eclipse.buckminster.core.materializer.IMaterializer;
import org.eclipse.buckminster.core.materializer.MaterializationJob;
import org.eclipse.buckminster.core.materializer.WorkspaceBindingInstallJob;
import org.eclipse.buckminster.core.metadata.MetadataSynchronizer;
import org.eclipse.buckminster.core.parser.IParserFactory;
import org.eclipse.buckminster.core.parser.ParserFactory;
import org.eclipse.buckminster.core.reader.ICatalogReader;
import org.eclipse.buckminster.core.reader.IReaderType;
import org.eclipse.buckminster.core.reader.MissingReaderTypeException;
import org.eclipse.buckminster.core.reader.RemoteFile;
import org.eclipse.buckminster.core.reader.RemoteFileCache;
import org.eclipse.buckminster.core.version.IQualifierGenerator;
import org.eclipse.buckminster.core.version.IVersionConverter;
import org.eclipse.buckminster.core.version.MissingMaterializerException;
import org.eclipse.buckminster.core.version.MissingVersionConverterException;
import org.eclipse.buckminster.runtime.Buckminster;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.BuckminsterPreferences;
import org.eclipse.buckminster.runtime.LogAwarePlugin;
import org.eclipse.buckminster.runtime.Logger;
import org.eclipse.buckminster.runtime.MonitorUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ecf.core.security.IConnectContext;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * The main plugin class to be used in the desktop.
 * 
 * @author Thomas Hallgren
 */
public class CorePlugin extends LogAwarePlugin {
	public static final String CORE_NAMESPACE = Buckminster.NAMESPACE + ".core"; //$NON-NLS-1$

	public static final String ANT_TASK_GENERATORS = CORE_NAMESPACE + ".anttaskGenerators"; //$NON-NLS-1$

	public static final String BOM_FILE = "buckminster.bom"; //$NON-NLS-1$

	public static final String COMPONENT_TYPE_POINT = CORE_NAMESPACE + ".componentTypes"; //$NON-NLS-1$

	public static final String CQUERY_FILE = "buckminster.cquery"; //$NON-NLS-1$

	public static final String CSPEC_BUILDER_POINT = CORE_NAMESPACE + ".cspecBuilders"; //$NON-NLS-1$

	public static final String CSPEC_FILE = "buckminster.cspec"; //$NON-NLS-1$

	public static final String CSPECEXT_FILE = "buckminster.cspex"; //$NON-NLS-1$

	public static final String FORCED_ACTIVATIONS_POINT = CORE_NAMESPACE + ".forcedActivations"; //$NON-NLS-1$

	public static final String QUALIFIER_GENERATOR_POINT = CORE_NAMESPACE + ".qualifierGenerators"; //$NON-NLS-1$

	public static final String READER_TYPE_POINT = CORE_NAMESPACE + ".readerTypes"; //$NON-NLS-1$

	public static final String VERSION_CONVERTERS_POINT = CORE_NAMESPACE + ".versionConverters"; //$NON-NLS-1$

	public static final String ACTORS_POINT = CORE_NAMESPACE + ".actors"; //$NON-NLS-1$

	public static final String INTERNAL_ACTORS_POINT = CORE_NAMESPACE + ".internalActors"; //$NON-NLS-1$

	public static final String BUCKMINSTER_PROJECT = ".buckminster"; //$NON-NLS-1$

	private static CorePlugin plugin;

	public static CorePlugin getDefault() {
		return plugin;
	}

	public static String getID() {
		return plugin.toString();
	}

	public static Logger getLogger() {
		CorePlugin corePlugin = plugin;
		if (corePlugin != null)
			return corePlugin.getBundleLogger();

		return new Logger(new ILog() {
			@Override
			public void addLogListener(ILogListener listener) {
			}

			@Override
			public Bundle getBundle() {
				return null;
			}

			@Override
			public void log(IStatus status) {
				if (status == null)
					return;
				switch (status.getSeverity()) {
					case IStatus.ERROR:
					case IStatus.WARNING:
						System.err.println(status.getMessage());
						break;
					default:
						System.out.println(status.getMessage());
				}
			}

			@Override
			public void removeLogListener(ILogListener listener) {
			}
		});
	}

	/**
	 * Returns the perform manager that coordinates all perform activity in the
	 * current workspace
	 * 
	 * @return the perform manager for the workspace
	 */
	public static IPerformManager getPerformManager() throws CoreException {
		return PerformManager.getInstance();
	}

	/**
	 * Returns the string from the plugin's resource bundle, or 'key' if not
	 * found.
	 */
	public static String getResourceString(String key) {
		ResourceBundle bundle = CorePlugin.getDefault().getResourceBundle();
		try {
			return (bundle != null) ? bundle.getString(key) : key;
		} catch (MissingResourceException e) {
			return key;
		}
	}

	public static void logWarningsAndErrors(IStatus status) {
		logWarningsAndErrors(status, new StringBuilder(), new HashSet<String>(), 0);
	}

	private static void logWarningsAndErrors(IStatus status, StringBuilder line, Set<String> unique, int indent) {
		switch (status.getSeverity()) {
			case IStatus.CANCEL:
				throw new OperationCanceledException();
			case IStatus.OK:
				break;
			default:
				line.setLength(0);
				if (indent > 0) {
					char leadIn;
					switch (status.getSeverity()) {
						case IStatus.ERROR:
							leadIn = 'E';
							break;
						case IStatus.WARNING:
							leadIn = 'W';
							break;
						default:
							leadIn = 'I';
					}
					line.append(leadIn);
					for (int idx = 1; idx < indent; ++idx)
						line.append(' ');
				}

				String msg = status.getMessage();
				if (msg != null)
					line.append(msg);

				Throwable reason = status.getException();
				if (reason != null) {
					String reasonMsg = reason.getMessage();
					if (reasonMsg == null)
						reasonMsg = reason.toString();
					if (msg == null)
						line.append(reasonMsg);
					else if (!msg.equals(reasonMsg)) {
						line.append(": "); //$NON-NLS-1$
						line.append(reasonMsg);
					}
				}
				String logMsg = line.toString();
				if (unique.add(logMsg))
					getLogger().log(status.getSeverity(), logMsg);
				HashSet<String> childUnique = new HashSet<String>();
				for (IStatus child : status.getChildren())
					logWarningsAndErrors(child, line, childUnique, indent + 2);
		}
	}

	private final RemoteFileCache remoteFileCache = new RemoteFileCache(30000, "bm-remote", ".cache", null); //$NON-NLS-1$ //$NON-NLS-2$

	private ResourceBundle resourceBundle;

	private final Map<String, Map<String, Object>> singletonExtensionCache = new HashMap<String, Map<String, Object>>();

	private final ShortDurationURLCache urlCache = new ShortDurationURLCache();

	private WorkspaceJob updatePrefsJob;

	private IProvisioningAgent resolverAgent;

	/**
	 * The constructor.
	 */
	public CorePlugin() {
		super();
		plugin = this;
	}

	/**
	 * Clear the remote file cache
	 */
	public void clearRemoteFileCache() {
		remoteFileCache.clear();
	}

	/**
	 * Clear the remote file cache
	 */
	public void clearURLCache() {
		urlCache.clear();
	}

	/**
	 * Generate a new UUID. We keep this in a separate method in case we'd like
	 * to change to type 1 at some point. For now, a random type 4 UUID is used.
	 * 
	 * @return A randomly generated type 4 UUID.
	 */
	public UUID createUUID() {
		return UUID.randomUUID();
	}

	/**
	 * Returns the special project <code>.buckminster</code> that acts as a
	 * placeholder for all artifacts that are not otherwise mapped into the
	 * workspace. The project will be created if the
	 * <code>createIfMissing</code> flag is <code>true</code> and opened if it
	 * was not so already.
	 * 
	 * @param createIfMissing
	 *            Set to true if the project should be created when its missing.
	 * @param monitor
	 *            The progress monitor
	 * @return The .buckminster project or <code>null</code> if its missing and
	 *         the <code>createIfMissing</code> parameter was false.
	 */
	public IProject getBuckminsterProject(boolean createIfMissing, IProgressMonitor monitor) throws CoreException {
		monitor = MonitorUtils.ensureNotNull(monitor);
		IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
		IProject bmProject = wsRoot.getProject(BUCKMINSTER_PROJECT);
		boolean exists = bmProject.exists();
		if (exists) {
			if (!bmProject.isOpen())
				bmProject.open(monitor);
			else
				MonitorUtils.complete(monitor);
			return bmProject;
		}

		if (!createIfMissing)
			return null;

		monitor.beginTask(null, 2000);
		monitor.subTask("Creating .buckminster project"); //$NON-NLS-1$
		IPath path = getBuckminsterProjectLocation();
		IPath defaultLocation = wsRoot.getLocation().append(CorePlugin.BUCKMINSTER_PROJECT);

		if (!path.equals(defaultLocation)) {
			IProjectDescription desc = ResourcesPlugin.getWorkspace().newProjectDescription(BUCKMINSTER_PROJECT);
			desc.setLocation(path);
			bmProject.create(desc, MonitorUtils.subMonitor(monitor, 1000));
		} else
			bmProject.create(MonitorUtils.subMonitor(monitor, 1000));

		bmProject.open(MonitorUtils.subMonitor(monitor, 1000));
		monitor.done();
		return bmProject;
	}

	/**
	 * Returns the absolute path in the local filesystem for the
	 * <code>.buckminster</code> project The project need not exist.
	 * 
	 * @return The location of the .buckminster project.
	 */
	public IPath getBuckminsterProjectLocation() {
		IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
		IProject bmProject = wsRoot.getProject(BUCKMINSTER_PROJECT);
		if (bmProject.exists())
			return bmProject.getLocation();

		IPath path = BuckminsterPreferences.getBuckminterProjectContents();
		if (path == null)
			path = wsRoot.getLocation().append(CorePlugin.BUCKMINSTER_PROJECT);
		return path;
	}

	public IComponentType getComponentType(String componentType) throws CoreException {
		IComponentType cType = getExecutableExtension(IComponentType.class, COMPONENT_TYPE_POINT, componentType, true);
		if (cType != null)
			return cType;
		throw new MissingComponentTypeException(componentType);
	}

	/**
	 * Obtain a executable extension from the platform extension registry. A
	 * cache is utilized to ensure a singleton pattern.
	 * 
	 * @param instanceClass
	 *            The class of the executable extension.
	 * @param extensionPoint
	 *            The extension point id.
	 * @param instanceId
	 *            The instance id.
	 * @param useSingelton
	 *            Set to true if the instance should be cached and returned on
	 *            subsequent calls.
	 * @return A singleton instance that corresponds to the extension.
	 * @throws CoreException
	 */
	public <T extends Object> T getExecutableExtension(Class<T> instanceClass, String extensionPoint, String instanceId, boolean useSingleton)
			throws CoreException {
		Object extension = null;
		Map<String, Object> singletonCache = null;
		if (useSingleton) {
			singletonCache = singletonExtensionCache.get(extensionPoint);
			if (singletonCache != null) {
				extension = singletonCache.get(instanceId);
				if (extension != null)
					return instanceClass.cast(extension);
			}
		}

		IExtensionRegistry exReg = Platform.getExtensionRegistry();
		IConfigurationElement[] elems = exReg.getConfigurationElementsFor(extensionPoint);

		for (IConfigurationElement elem : elems) {
			if (elem.getAttribute("id").equals(instanceId)) //$NON-NLS-1$
			{
				extension = elem.createExecutableExtension("class"); //$NON-NLS-1$
				if (useSingleton) {
					if (singletonCache == null) {
						singletonCache = new HashMap<String, Object>();
						singletonExtensionCache.put(extensionPoint, singletonCache);
					}
					singletonCache.put(instanceId, extension);
				}
				return instanceClass.cast(extension);
			}
		}
		return null;
	}

	/**
	 * Returns the known ids for the given extension point
	 * 
	 * @param extensionPoint
	 * @return
	 */
	public String[] getExtensionIds(String extensionPoint) {
		IExtensionRegistry exReg = Platform.getExtensionRegistry();
		IConfigurationElement[] elems = exReg.getConfigurationElementsFor(extensionPoint);
		int idx = elems.length;
		String[] ids = new String[idx];
		while (--idx >= 0)
			ids[idx] = elems[idx].getAttribute("id"); //$NON-NLS-1$
		return ids;
	}

	public IMaterializer getMaterializer(String materializerId) throws CoreException {
		if (materializerId == null)
			materializerId = IMaterializer.WORKSPACE;

		IMaterializer mat = getExecutableExtension(IMaterializer.class, IMaterializer.MATERIALIZERS_POINT, materializerId, true);
		if (mat != null)
			return mat;
		throw new MissingMaterializerException(materializerId);
	}

	public IParserFactory getParserFactory() {
		return ParserFactory.getDefault();
	}

	public IQualifierGenerator getQualifierGenerator(String qualifierGenerator) throws CoreException {
		IQualifierGenerator vm = getExecutableExtension(IQualifierGenerator.class, QUALIFIER_GENERATOR_POINT, qualifierGenerator, true);
		if (vm != null)
			return vm;
		throw BuckminsterException.fromMessage(NLS.bind(Messages.Missing_qualifier_generator_for_id_0, qualifierGenerator));
	}

	public IReaderType getReaderType(String readerType) throws CoreException {
		IReaderType vm = getExecutableExtension(IReaderType.class, READER_TYPE_POINT, readerType, true);
		if (vm != null)
			return vm;
		throw new MissingReaderTypeException(readerType);
	}

	/**
	 * Locates the extension {@link #CSPEC_BUILDER_POINT} and creates a
	 * resolution builder.
	 * 
	 * @param id
	 *            The id of the desired builder
	 * @return A resolution builder.
	 * @throws CoreException
	 *             if no builder can be found with the given id or if something
	 *             goes wrong when instantiating it.
	 */
	public IResolutionBuilder getResolutionBuilder(String id) throws CoreException {
		IResolutionBuilder builder = getExecutableExtension(IResolutionBuilder.class, CSPEC_BUILDER_POINT, id, false);
		if (builder != null)
			return builder;
		throw new MissingBuilderException(id);
	}

	public synchronized IProvisioningAgent getResolverAgent() throws CoreException {
		if (resolverAgent == null) {
			Buckminster bucky = Buckminster.getDefault();
			IProvisioningAgentProvider agentProvider = bucky.getService(IProvisioningAgentProvider.class);
			try {
				File agentLocation = getResolverAgentLocation();
				resolverAgent = agentProvider.createAgent(agentLocation.toURI());
			} finally {
				bucky.ungetService(agentProvider);
			}
		}
		return resolverAgent;
	}

	public File getResolverAgentLocation() {
		return getStateLocation().append("p2.resolver").toFile(); //$NON-NLS-1$
	}

	/**
	 * Returns the plugin's resource bundle,
	 */
	public ResourceBundle getResourceBundle() {
		try {
			if (resourceBundle == null)
				resourceBundle = ResourceBundle.getBundle(CORE_NAMESPACE + ".CorePluginResources"); //$NON-NLS-1$
		} catch (MissingResourceException x) {
			resourceBundle = null;
		}
		return resourceBundle;
	}

	public IVersionConverter getVersionConverter(String versionConverter) throws CoreException {
		if (versionConverter == null)
			versionConverter = "tag"; //$NON-NLS-1$

		IVersionConverter vc = getExecutableExtension(IVersionConverter.class, VERSION_CONVERTERS_POINT, versionConverter, false);
		if (vc != null)
			return vc;
		throw new MissingVersionConverterException(versionConverter);
	}

	/**
	 * Opens a remote file using the short duration cache maintained by this
	 * plugin.
	 * 
	 * @param remoteFile
	 *            the file to open
	 * @return An opened stream to the cached entry.
	 * @throws IOException
	 */
	public InputStream openCachedRemoteFile(ICatalogReader reader, String fileName, IProgressMonitor monitor) throws IOException, CoreException {
		return remoteFileCache.openRemoteFile(new RemoteFile(reader, fileName), monitor);
	}

	/**
	 * Opens a url using the short duration cache maintained by this plugin.
	 * 
	 * @param url
	 * @return input stream for the url
	 * @throws IOException
	 */
	public InputStream openCachedURL(URL url, IConnectContext cctx, IProgressMonitor monitor) throws IOException, CoreException {
		return urlCache.openURL(url, cctx, monitor);
	}

	/**
	 * This method is called upon plug-in activation
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);

		Job startJob = new Job("Core plugin starter") //$NON-NLS-1$
		{
			@Override
			public IStatus run(IProgressMonitor monitor) {
				IExtensionRegistry exReg = Platform.getExtensionRegistry();
				if (exReg == null)
					return Status.OK_STATUS; // We died before we got the chance

				MetadataSynchronizer.setUp();
				MaterializationJob.setUp();
				IConfigurationElement[] forcedActivations = exReg.getConfigurationElementsFor(FORCED_ACTIVATIONS_POINT);
				monitor.beginTask(null, forcedActivations.length);
				for (IConfigurationElement elem : forcedActivations) {
					String pluginId = elem.getAttribute("pluginId"); //$NON-NLS-1$
					try {
						Bundle bundle = Platform.getBundle(pluginId);
						bundle.loadClass(elem.getAttribute("class")); //$NON-NLS-1$
					} catch (Exception e) {
						getLogger().warning(e, NLS.bind(Messages.Unable_to_activate_bundle_0, pluginId));
					}
					monitor.worked(1);
				}
				monitor.done();

				WorkspaceBindingInstallJob.start();
				return Status.OK_STATUS;
			}
		};
		startJob.schedule(100);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		if (resolverAgent != null)
			resolverAgent.stop();
		super.stop(context);
	}

	public synchronized void stopAllJobs() throws Exception {
		if (updatePrefsJob != null) {
			updatePrefsJob.cancel();
			updatePrefsJob.join();
			updatePrefsJob = null;
		}
	}
}
