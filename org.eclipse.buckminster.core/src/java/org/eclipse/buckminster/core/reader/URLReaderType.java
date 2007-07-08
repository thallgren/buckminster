/*******************************************************************************
 * Copyright (c) 2004, 2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/

package org.eclipse.buckminster.core.reader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import org.eclipse.buckminster.core.CorePlugin;
import org.eclipse.buckminster.core.common.model.Format;
import org.eclipse.buckminster.core.cspec.model.ComponentRequest;
import org.eclipse.buckminster.core.ctype.IComponentType;
import org.eclipse.buckminster.core.query.builder.ComponentQueryBuilder;
import org.eclipse.buckminster.core.resolver.NodeQuery;
import org.eclipse.buckminster.core.resolver.ResolutionContext;
import org.eclipse.buckminster.core.rmap.model.Provider;
import org.eclipse.buckminster.core.rmap.model.ProviderScore;
import org.eclipse.buckminster.core.version.ProviderMatch;
import org.eclipse.buckminster.core.version.VersionMatch;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.URLUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * @author thhal
 */
public class URLReaderType extends AbstractReaderType
{
	private static final ThreadLocal<ProviderMatch> s_currentProviderMatch = new InheritableThreadLocal<ProviderMatch>();

	public static IComponentReader getReader(URL externalFile, IProgressMonitor monitor) throws CoreException
	{
		return getDirectReader(externalFile, IReaderType.URL, monitor);
	}

	public static ProviderMatch getCurrentProviderMatch()
	{
		return s_currentProviderMatch.get();
	}

	static IComponentReader getDirectReader(URL url, String readerType, IProgressMonitor monitor) throws CoreException
	{
		String urlString = url.toString();
		ComponentRequest rq = new ComponentRequest(urlString, null, null);
		ComponentQueryBuilder queryBld = new ComponentQueryBuilder();
		queryBld.setRootRequest(rq);
		ResolutionContext context = new ResolutionContext(queryBld.createComponentQuery());
		NodeQuery nq = new NodeQuery(context, rq, null);

		IComponentType ctype = CorePlugin.getDefault().getComponentType(IComponentType.UNKNOWN);
		Provider provider = new Provider(readerType, new String[] { ctype.getId() }, null, new Format(urlString), null, false, false, null);
		ProviderMatch pm = new ProviderMatch(provider, ctype, VersionMatch.DEFAULT, ProviderScore.GOOD, nq);
		return provider.getReaderType().getReader(pm, monitor);
	}

	@Override
	public URL convertToURL(String repositoryLocator, VersionMatch versionSelector) throws CoreException
	{
		try
		{
			return URLUtils.normalizeToURL(repositoryLocator);
		}
		catch(MalformedURLException e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	public IReaderType getLocalReaderType()
	{
		return this;
	}

	public IComponentReader getReader(ProviderMatch providerMatch, IProgressMonitor monitor) throws CoreException
	{
		ProviderMatch oldMatch = s_currentProviderMatch.get();
		s_currentProviderMatch.set(providerMatch);
		try
		{
			URLFileReader reader = new URLFileReader(this, providerMatch);
			if(!reader.exists(monitor))
				throw new FileNotFoundException(reader.getURL().toString());
			return reader;
		}
		catch(IOException e)
		{
			throw BuckminsterException.wrap(e);
		}
		finally
		{
			s_currentProviderMatch.set(oldMatch);
		}
	}

	public URI getURI(Provider provider, Map<String,String> properties) throws CoreException
	{
		return getURI(provider.getURI(properties));
	}

	public URI getURI(ProviderMatch providerMatch) throws CoreException
	{
		return getURI(providerMatch.getRepositoryURI());
	}

	public URI getURI(String repository) throws CoreException
	{
		URI uri;
		try
		{
			uri = new URI(repository);
		}
		catch(URISyntaxException e)
		{
			if(repository.indexOf(' ') < 0)
				throw BuckminsterException.wrap(e);

			try
			{
				uri = new URI(repository.replaceAll("\\s", "%20"));
			}
			catch(URISyntaxException e2)
			{
				throw BuckminsterException.wrap(e2);
			}
		}

		String scheme = uri.getScheme();
		String auth = uri.getAuthority();
		String path = uri.getPath();
		String query = uri.getQuery();
		String fragment = uri.getFragment();
		boolean change = false;
		if(!(isFileReader() || path.endsWith("/")))
		{
			path += "/";
			change = true;
		}

		if(scheme == null)
		{
			scheme = "file";
			change = true;
		}

		try
		{
			if(change)
				uri = new URI(scheme, auth, path, query, fragment);
			return uri;
		}
		catch(URISyntaxException e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	@Override
	public boolean isFileReader()
	{
		return true;
	}

	@Override
	public String getRemotePath(String repositoryLocation) throws CoreException
	{
		return getURI(repositoryLocation).getPath();
	}
}
