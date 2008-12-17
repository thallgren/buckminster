/*****************************************************************************
 * Copyright (c) 2006-2007, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/

package org.eclipse.buckminster.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.eclipse.buckminster.core.common.model.ExpandingProperties;
import org.eclipse.buckminster.core.cspec.IAction;
import org.eclipse.buckminster.core.cspec.IAttribute;
import org.eclipse.buckminster.core.cspec.IComponentRequest;
import org.eclipse.buckminster.core.cspec.QualifiedDependency;
import org.eclipse.buckminster.core.cspec.model.Action;
import org.eclipse.buckminster.core.cspec.model.ComponentName;
import org.eclipse.buckminster.core.cspec.model.ComponentRequest;
import org.eclipse.buckminster.core.helpers.BMProperties;
import org.eclipse.buckminster.core.helpers.FilterUtils;
import org.eclipse.buckminster.core.helpers.UnmodifiableMapUnion;
import org.eclipse.buckminster.core.metadata.model.Resolution;
import org.eclipse.buckminster.core.query.model.ComponentQuery;
import org.eclipse.buckminster.core.resolver.NodeQuery;
import org.eclipse.buckminster.runtime.Logger;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.IValueVariable;
import org.eclipse.core.variables.VariablesPlugin;

/**
 * The <i>Resolution and Materialization</i> context. Maintains information with a lifecycle that lasts throughout the
 * resolution and materialization process.
 * 
 * @author Thomas Hallgren
 */
public class RMContext extends ExpandingProperties<Object>
{
	public class TagInfo
	{
		private final String m_tagId;

		private final String m_infoString;

		private boolean m_used = false;

		private TagInfo(String infoString)
		{
			m_tagId = String.format("%04d", new Integer(++m_tagInfoSquenceNumber)); //$NON-NLS-1$
			m_infoString = infoString;
		}

		public String getTagId()
		{
			return m_tagId;
		}

		public boolean isUsed()
		{
			return m_used;
		}

		public void setUsed()
		{
			m_used = true;
		}

		@Override
		public String toString()
		{
			return "TAG-ID " + m_tagId + " = " + m_infoString; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	public static String formatStatus(IStatus status)
	{
		StringWriter bld = new StringWriter();
		BufferedWriter wrt = new BufferedWriter(bld);
		try
		{
			formatStatus(wrt, 0, status);
			wrt.flush();
		}
		catch(IOException e)
		{
		}
		return bld.toString();
	}

	private static IStatus addTagId(String tagId, IStatus status)
	{
		if(status instanceof MultiStatus)
		{
			IStatus[] children = status.getChildren();
			int idx = children.length;
			IStatus[] taggedChildren = new IStatus[idx];
			while(--idx >= 0)
				taggedChildren[idx] = addTagId(tagId, children[idx]);
			return new MultiStatus(status.getPlugin(), status.getCode(), taggedChildren, addTagId(tagId, status
					.getMessage()), status.getException());
		}
		return new Status(status.getSeverity(), status.getPlugin(), addTagId(tagId, status.getMessage()), status
				.getException());
	}

	private static String addTagId(String tagId, String msg)
	{
		String prefix = '[' + tagId + "] : "; //$NON-NLS-1$
		if(msg == null)
			msg = prefix;
		else if(!msg.startsWith(prefix))
			msg = prefix + msg;
		return msg;
	}

	private static void formatStatus(BufferedWriter wrt, int indent, IStatus status) throws IOException
	{
		for(int idx = 0; idx < indent; ++idx)
			wrt.append(' ');
		switch(status.getSeverity())
		{
		case IStatus.INFO:
			wrt.append("INFO    "); //$NON-NLS-1$
			break;
		case IStatus.WARNING:
			wrt.append("WARNING "); //$NON-NLS-1$
			break;
		case IStatus.ERROR:
			wrt.append("ERROR   "); //$NON-NLS-1$
			break;
		}
		wrt.append(status.getMessage());
		for(IStatus child : status.getChildren())
		{
			wrt.newLine();
			formatStatus(wrt, indent + 2, child);
		}
	}

	private int m_tagInfoSquenceNumber = 0;

	private final Map<String, TagInfo> m_knownTagInfos = new HashMap<String, TagInfo>();

	private final Map<QualifiedDependency, NodeQuery> m_nodeQueries = new HashMap<QualifiedDependency, NodeQuery>();

	private final Map<String, String[]> m_filterAttributeUsageMap = new HashMap<String, String[]>();

	private static final Map<String, String> s_staticAdditions;

	static
	{
		Map<String, String> additions = new HashMap<String, String>();
		File homeFile = TargetPlatform.getPlatformInstallLocation();
		if(homeFile != null)
		{
			CorePlugin.getLogger().debug("Platform install location: %s", homeFile); //$NON-NLS-1$
			additions.put("eclipse.home", homeFile.toString()); //$NON-NLS-1$
		}
		else
			CorePlugin.getLogger().debug("Platform install location is NULL!"); //$NON-NLS-1$

		additions.put("workspace.root", ResourcesPlugin.getWorkspace().getRoot().getLocation().toPortableString()); //$NON-NLS-1$
		try
		{
			additions.put("localhost", InetAddress.getLocalHost().getHostName()); //$NON-NLS-1$
		}
		catch(UnknownHostException e1)
		{
			// We'll just have to do without it.
		}
		s_staticAdditions = additions;
	}

	public static Map<String, ? extends Object> getGlobalPropertyAdditions()
	{
		Map<String, String> sysProps = BMProperties.getSystemProperties();
		IStringVariableManager varMgr = VariablesPlugin.getDefault().getStringVariableManager();
		IValueVariable[] vars = varMgr.getValueVariables();

		Map<String, String> additions = new HashMap<String, String>(s_staticAdditions.size() + sysProps.size()
				+ vars.length + 6);
		additions.putAll(s_staticAdditions);

		try
		{
			ITargetPlatform tf = TargetPlatform.getInstance();
			additions.put(TargetPlatform.TARGET_OS, tf.getOS());
			additions.put(TargetPlatform.TARGET_WS, tf.getWS());
			additions.put(TargetPlatform.TARGET_ARCH, tf.getArch());
			additions.put(TargetPlatform.TARGET_NL, tf.getNL());
			additions.put(TargetPlatform.TARGET_LOCATION, tf.getLocation().toString());
		}
		catch(CoreException e)
		{
			e.printStackTrace();
		}
		for(IValueVariable var : varMgr.getValueVariables())
			additions.put(var.getName(), var.getValue());

		additions.putAll(sysProps);
		return additions;
	}

	private boolean m_continueOnError;

	private final Map<ComponentRequest, TagInfo> m_tagInfos = new HashMap<ComponentRequest, TagInfo>();

	private final Map<UUID, Object> m_userCache = Collections.synchronizedMap(new HashMap<UUID, Object>());

	private final Map<String, String> m_bindingProperties = Collections.synchronizedMap(new HashMap<String, String>());

	private MultiStatus m_status;

	private boolean m_silentStatus;

	public RMContext(Map<String, ? extends Object> properties)
	{
		this(properties, null);
	}

	public RMContext(Map<String, ? extends Object> properties, RMContext source)
	{
		super(getGlobalPropertyAdditions());
		putAll(properties, true);
		if(source != null)
		{
			m_userCache.putAll(source.getUserCache());
			m_tagInfos.putAll(source.getTagInfos());
			m_bindingProperties.putAll(source.getBindingProperties());
			m_filterAttributeUsageMap.putAll(source.getFilterAttributeUsageMap());
		}
	}

	/**
	 * This is where the exceptions that occur during processing will end up if the {@link #isContinueOnError()} returns
	 * <code>true</code>.
	 * 
	 * @param resolveStatus
	 *            A status that indicates an error during processing.
	 */
	public synchronized void addRequestStatus(IComponentRequest request, IStatus status)
	{
		Logger logger = CorePlugin.getLogger();
		if(logger.isInfoEnabled())
			status = addTagId(getTagId(request), status);

		if(!isSilentStatus())
		{
			switch(status.getSeverity())
			{
			case IStatus.ERROR:
				logger.error(formatStatus(status));
				break;
			case IStatus.WARNING:
				logger.warning(formatStatus(status));
				break;
			case IStatus.INFO:
				logger.info(formatStatus(status));
			}
		}

		if(m_status == null)
		{
			m_status = new MultiStatus(CorePlugin.getID(), IStatus.OK, status instanceof MultiStatus
					? ((MultiStatus)status).getChildren()
					: new IStatus[] { status }, "Errors and Warnings", null); //$NON-NLS-1$
		}
		else
			m_status.merge(status);
	}

	public synchronized void addTagInfo(ComponentRequest request, String info)
	{
		TagInfo tagInfo = m_tagInfos.get(request);
		if(tagInfo == null)
		{
			tagInfo = m_knownTagInfos.get(info);
			if(tagInfo == null)
			{
				tagInfo = new TagInfo(info);
				m_knownTagInfos.put(info, tagInfo);
			}
			m_tagInfos.put(request, tagInfo);
		}
	}

	/**
	 * Clears the status so that next call to {@link #getStatus()} returns {@link IStatus#OK_STATUS}.
	 */
	public synchronized void clearStatus()
	{
		m_status = null;
	}

	/**
	 * Emit all tags for warnings and errors that have been added earlier.
	 * 
	 * @return <code>true</code> if errors have been added, <code>false</code> if not.
	 */
	public boolean emitWarningAndErrorTags()
	{
		IStatus status = getStatus();
		switch(status.getSeverity())
		{
		case IStatus.ERROR:
			emitTagInfos();
			return true;
		case IStatus.WARNING:
		case IStatus.INFO:
			emitTagInfos();
		}
		return false;
	}

	public String getBindingName(Resolution resolution, Map<String, ? extends Object> props) throws CoreException
	{
		ComponentRequest request = resolution.getRequest();
		String name = null;

		IAttribute bindEntryPoint = resolution.getCSpec().getBindEntryPoint();
		if(bindEntryPoint instanceof IAction)
		{
			if(props == null)
				props = getProperties(request);
			name = ((Action)bindEntryPoint).getBindingName(props);
		}

		if(name == null)
			name = request.getName();
		return name;
	}

	public Map<String, String> getBindingProperties()
	{
		return m_bindingProperties;
	}

	public ComponentQuery getComponentQuery()
	{
		return null;
	}

	/**
	 * Returns the map used for keeping track of that attributes that are used by filters throughout the resolution and
	 * what values that are queried for those attributes.
	 * 
	 * @return An map, keyed by attribute name, that collects queried values for each attribute.
	 */
	public Map<String, String[]> getFilterAttributeUsageMap()
	{
		return m_filterAttributeUsageMap;
	}

	public NodeQuery getNodeQuery(ComponentRequest request)
	{
		return getNodeQuery(new QualifiedDependency(request, getComponentQuery().getAttributes(request)));
	}

	public synchronized NodeQuery getNodeQuery(QualifiedDependency qualifiedDependency)
	{
		NodeQuery query = m_nodeQueries.get(qualifiedDependency);
		if(query == null)
		{
			query = new NodeQuery(this, qualifiedDependency);
			m_nodeQueries.put(qualifiedDependency, query);
		}
		return query;
	}

	public Map<String, ? extends Object> getProperties(ComponentName cName)
	{
		return new UnmodifiableMapUnion<String, Object>(cName.getProperties(), this);
	}

	public NodeQuery getRootNodeQuery()
	{
		return getNodeQuery(getComponentQuery().getRootRequest());
	}

	/**
	 * Returns the status that reflects the outcome of the process. If the status is
	 * {@link org.eclipse.core.runtime.Status#OK_STATUS OK_STATUS} everything went OK.
	 * 
	 * @return The status of the process
	 */
	public IStatus getStatus()
	{
		IStatus status = m_status;
		if(status == null)
			return Status.OK_STATUS;

		while(status.isMultiStatus() && status.getChildren().length == 1)
			status = status.getChildren()[0];

		return status;
	}

	public synchronized Map<ComponentRequest, TagInfo> getTagInfos()
	{
		return m_tagInfos;
	}

	/**
	 * Returns a map intended for caching purposes during resolution and materialization. The map is synchronized. Users
	 * of the map must create UUID's to use as keys in the map.
	 * 
	 * @return A map to be used for caching purposes
	 */
	public Map<UUID, Object> getUserCache()
	{
		return m_userCache;
	}

	public boolean isContinueOnError()
	{
		return m_continueOnError;
	}

	public boolean isSilentStatus()
	{
		return m_silentStatus;
	}

	@Override
	public Object put(String key, Object value)
	{
		if("*".equals(value))
			value = FilterUtils.MATCH_ALL_OBJ;
		return super.put(key, value);
	}

	@Override
	public Object put(String key, Object value, boolean mutable)
	{
		if("*".equals(value))
			value = FilterUtils.MATCH_ALL_OBJ;
		return super.put(key, value, mutable);
	}

	public void setContinueOnError(boolean flag)
	{
		m_continueOnError = flag;
	}

	public void setSilentStatus(boolean flag)
	{
		m_silentStatus = flag;
	}

	private void emitTagInfos()
	{
		Logger logger = CorePlugin.getLogger();
		if(!logger.isInfoEnabled())
			return;

		Map<String, TagInfo> sorted = new TreeMap<String, TagInfo>();
		for(TagInfo tagInfo : m_tagInfos.values())
			if(tagInfo.isUsed())
				sorted.put(tagInfo.getTagId(), tagInfo);

		if(sorted.size() == 0)
			return;

		StringWriter bld = new StringWriter();
		BufferedWriter wrt = new BufferedWriter(bld);
		try
		{
			for(TagInfo tagInfo : sorted.values())
			{
				wrt.write(tagInfo.toString());
				wrt.newLine();
			}
			wrt.flush();
		}
		catch(IOException e)
		{
			// On a StringWriter? Don't think so.
		}
		logger.info(bld.toString());
	}

	private synchronized String getTagId(IComponentRequest request)
	{
		TagInfo tagInfo = m_tagInfos.get(request);
		if(tagInfo != null)
		{
			tagInfo.setUsed();
			return tagInfo.getTagId();
		}
		return "0000"; //$NON-NLS-1$
	}
}
