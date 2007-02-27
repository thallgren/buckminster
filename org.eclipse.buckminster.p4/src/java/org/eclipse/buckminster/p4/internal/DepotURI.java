/*******************************************************************************
 * Copyright (c) 2004, 2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/

package org.eclipse.buckminster.p4.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.buckminster.core.common.model.ExpandingProperties;
import org.eclipse.buckminster.core.helpers.BuckminsterException;
import org.eclipse.buckminster.core.helpers.TextUtils;
import org.eclipse.buckminster.core.version.VersionSelectorFactory;
import org.eclipse.buckminster.p4.preferences.Client;
import org.eclipse.buckminster.p4.preferences.P4Preferences;
import org.eclipse.buckminster.p4.preferences.Server;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.osgi.service.prefs.BackingStoreException;


/**
 * @author thhal
 */
public class DepotURI extends PropertyScope
{
	private final IPath m_depotPath;

	private final String m_address;

	private final Client m_client;

	private final String m_defaultBranch;

	private final boolean m_hasBranchDesignator;

	/**
	 * @see #DepotURI(URI, String, Map)
	 * @param urlString
	 * @param branch
	 * @param properties
	 * @throws CoreException
	 */
	public DepotURI(String urlString, String branch, Map<String,String> properties) throws CoreException
	{
		this(createURI(urlString), branch, properties);
	}

	/**
	 * <p>The repository urlString of the p4 provider should be in the form of
	 * a hierarchical URI using the (optional) scheme quot;p4:&quot;. As opposed to
	 * a Perforce Depot Path that starts with &quot;//&quot; in order to differentiate a depot
	 * from a local file, this uri uses &quot;//&quot; like a normal URL. The path of the
	 * this URL always denotes and must should start with &quot;/&quot;.</p>
	 * <p>An path segment consiting of a sinlge &quot;-&quot; is considered a branch
	 * designator. If this element is present, and if the <code>branch</code> argument is
	 * not <code>null</code>, it will be replaced by the <code>branch</code> argument. If
	 * the element is present but the <code>branch</code> argument is <code>null</code>, the resulting
	 * DepotURI will be truncated at that element. It will thus act as the root for the branches.
	 * This mechanism is used by the {@link VersionFinder}.</p>
	 * <p>The uri may also contain two properties expressed in the query section of the
	 * uri. The properties are:<dl>
	 * <dt>client</dt><dd>The name of the client</dd>
	 * <dt>defaultBranch</dt><dd>The name of the default branch</dd>
	 * </dl>
	 * </p>
	 * <p>If the <code>branch</code> argument is equal to {@link VersionSelectorFactory#DEFAULT_BRANCH}
	 * it will be replaced by the default branch of the uri (see below).</p> 
	 * Example:
	 * <pre>
	 *     p4://public.perforce.com:1666/public/perforce/api/java/p4package?client=public
	 * </pre>
	 * 
	 * This would cause a lookup for the server name
	 * &quot;public.perforce.com&quot; on port 1666. The depot path would be
	 * &quot;//public/perforce/api/java/...&quot; and the client for the
	 * connection would be named &quot;public&quot;
	 * @param uri The uri as described above.
	 * @param branch The branch to use at branch designator position or null if the resulting
	 * DepotURI will be used when locating available branches.
	 * @param properties The property scope.
	 */
	public DepotURI(URI uri, String branch, Map<String,String> properties) throws CoreException
	{
		super(properties);
		String scheme = uri.getScheme();
		if(!(scheme == null || "p4".equals(scheme)))
			throw new BuckminsterException("Invalid URI: " + uri.toString() + ", Scheme is not p4");

		if(uri.getUserInfo() != null)
			throw new BuckminsterException("Invalid URI: " + uri.toString() + ", P4 URI cannot contain user info");

		String defaultBranch = null;
		String clientName = null;
		String[] pairs = TextUtils.decodeToQueryPairs(uri.getQuery());
		for(String pair : pairs)
		{
			// now split the pair on the first '=' only
			// (one '=' is required to be there, even if the value is blank)
			//
			String[] kv = pair.split("=", 2);
			if("client".equalsIgnoreCase(kv[0]))
				clientName = kv[1];
			else if("defaultbranch".equalsIgnoreCase(kv[0]))
				defaultBranch = kv[1];
		}

		Server server = getServer(properties, uri);
		m_address = ExpandingProperties.expand(properties, server.getName(), 0);
		m_client = getClient(properties, server, clientName);
		m_defaultBranch = defaultBranch;

		if(VersionSelectorFactory.DEFAULT_BRANCH.equals(branch) && defaultBranch != null)
			branch = defaultBranch;

		// Create the UNC path that points into the DEPOT
		//
		IPath depotPath = new Path("/" + uri.getPath());
		
		// Check if we have a segment that is one single '-'. If we do, it
		// will be the branch designator.
		//
		int segmentCount = depotPath.segmentCount();
		int branchDesignator = segmentCount;
		while(--branchDesignator >= 0)
			if("-".equals(depotPath.segment(branchDesignator)))
				break;

		m_hasBranchDesignator = branchDesignator >= 0;
		if(m_hasBranchDesignator)
		{
			// Insert the branch at designated position or truncate the path at
			// that position if no branch is given.
			//
			IPath branchInjected;
			if(branchDesignator == 0)
				branchInjected = new Path("//");
			else
				branchInjected = depotPath.removeLastSegments(segmentCount - branchDesignator);

			if(branch != null)
			{
				branchInjected = branchInjected.append(branch);
				if(branchDesignator + 1 < segmentCount)
					branchInjected = branchInjected.append(depotPath.removeFirstSegments(branchDesignator + 1));
			}
			m_depotPath = branchInjected;
		}
		else
			m_depotPath = depotPath;
	}

	public static Client getClient(Map<String,String> scope, URI uri) throws CoreException
	{
		return getClient(scope, getServer(scope, uri), uri.getFragment());
	}

	public static Client getClient(Map<String,String> scope, Server server, String clientName) throws CoreException
	{
		try
		{
			Client client;
			if(clientName == null)
				client = server.getDefaultClient();
			else
			{
				client = server.getClient(clientName);
				if(client == null)
					throw new BuckminsterException("No preferences for P4 client " +
						clientName + " for server " + server.getName());
			}
			return client;
		}
		catch(BackingStoreException e)
		{
			throw new BuckminsterException(e.getMessage());
		}
	}
	public static URI createURI(String uriString) throws CoreException
	{
		try
		{
			return new URI(uriString);
		}
		catch(URISyntaxException e)
		{
			throw new BuckminsterException("Invalid URL used for P4 provider: " + uriString, e);
		}
	}

	public static Server getServer(Map<String,String> scope, URI uri) throws CoreException
	{
		try
		{
			P4Preferences prefs = P4Preferences.getInstance();
			String address = uri.getHost();
			Server server;
			if(address == null)
			{
				server = prefs.getDefaultServer();
				if(server == null)
					server = prefs.configureDefaultServer(scope, false);
			}
			else
			{
				server = prefs.getServer(address);
				if(server == null)
					throw new BuckminsterException("No P4 server with address " + address + " has been configured");
			}
			return server;
		}
		catch(BackingStoreException e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	/**
	 * @return Returns the client.
	 */
	public final String getClientName()
	{
		return this.expand(m_client.getName());
	}

	/**
	 * @return Returns the local root
	 */
	public String getLocalRoot()
	{
		return this.expand(m_client.getLocalRoot());
	}

	public IPath getMappingForDepot(IPath depotPath) throws CoreException
	{
		try
		{
			return m_client.getMappingForDepot(depotPath);
		}
		catch(BackingStoreException e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	/**
	 * @return The depot path.
	 */
	public final IPath getDepotPath()
	{
		return m_depotPath;
	}

	/**
	 * @return Client preferences.
	 */
	public final Client getClient()
	{
		return m_client;
	}

	/**
	 * @return The password.
	 */
	public final String getPassword()
	{
		return m_client.getServer().getPassword();
	}

	/**
	 * @return Returns the port.
	 */
	public final String getAddress()
	{
		return m_address;
	}

	public final String getDefaultBranch()
	{
		return m_defaultBranch;
	}

	public final boolean hasBranchDesignator()
	{
		return m_hasBranchDesignator;
	}

	/**
	 * @return Returns the user.
	 */
	public final String getUser()
	{
		return this.expand(m_client.getServer().getUser());
	}

	/**
	 * Compares <code>p1</code> and <code>p2</code> for equality. The last segment of the paths will
	 * be stripped of prior to comparison if it is the &quot;...&quot; segment.
	 * @param p1
	 * @param p2
	 * @return <code>true</code> if the paths are equal.
	 */
	public static final boolean pathEquals(IPath p1, IPath p2)
	{
		if(p1 == p2)
			return true;
		if(p1 == null || p2 == null)
			return false;
		if(p1.lastSegment().equals("..."))
			p1 = p1.removeLastSegments(1);
		if(p2.lastSegment().equals("..."))
			p2 = p2.removeLastSegments(1);
		return p1.toFile().equals(p2.toFile());
	}

	private static Pattern s_p4PortPattern = Pattern.compile("^([^:]+):([0-9]+)$");

	@Override
	public String toString()
	{
		try
		{
			String host = this.expand(m_client.getServer().getName());
			int port = -1;
			Matcher matcher = s_p4PortPattern.matcher(host);
			if(matcher.matches())
			{
				host = matcher.group(1);
				port = Integer.parseInt(matcher.group(2));
			}
			return new URI("p4", null, host, port, m_depotPath.makeUNC(false).toString(), null, this.expand(m_client.getName())).toString();
		}
		catch(URISyntaxException e)
		{
			throw new RuntimeException(e);
		}
	}
}
