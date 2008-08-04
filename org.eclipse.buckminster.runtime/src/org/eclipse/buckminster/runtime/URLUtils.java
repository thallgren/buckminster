/*****************************************************************************
 * Copyright (c) 2006-2007, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.runtime;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * @author Thomas Hallgren
 */
public abstract class URLUtils
{
	/**
	 * Appends a trailing slash to <code>url</code> and returns the result. If the <code>url</code> already has a
	 * trailing slash, the argument is returned without modification.
	 * 
	 * @param url
	 *            The <code>url</code> that should receive the trailing slash. Cannot be <code>null</code>.
	 * @return A <code>url</code> that has a trailing slash
	 */
	public static URL appendTrailingSlash(URL url)
	{
		if(!url.getPath().endsWith("/"))
		{
			try
			{
				URI u = url.toURI();
				url = new URI(u.getScheme(), u.getAuthority(), u.getPath() + '/', u.getQuery(), u.getFragment())
						.toURL();
			}
			catch(RuntimeException e)
			{
				throw e;
			}
			catch(Exception e)
			{
				// Not very likely since original was a URL.
				//
				throw new RuntimeException(e);
			}
		}
		return url;
	}

	/**
	 * Append <code>path</code> to <code>url</code> while preserving all other characteristics of the
	 * <code>url</code>. If <code>path</code> is absolute, it will become the new path of the <code>url</code>
	 * else, if <code>url</code> doesn't end with a trailing slash, it is appended prior to appending the
	 * <code>path</code>.
	 * 
	 * @param url
	 *            The url to use as root.
	 * @param path
	 *            The path to append
	 * 
	 * @return The url with the new path.
	 */
	public static URL appendPath(URL url, IPath path)
	{
		if(path == null || path.segmentCount() == 0)
			return url;

		try
		{
			URI u = url.toURI();
			String urlPath;

			path = path.setDevice(null);
			if(path.isAbsolute())
				urlPath = path.toPortableString();
			else
			{
				urlPath = u.getPath();
				if(urlPath == null || urlPath.length() == 0)
					urlPath = path.makeAbsolute().toPortableString();
				else
				{
					StringBuilder bld = new StringBuilder();
					bld.append(urlPath);
					if(!urlPath.endsWith("/"))
						bld.append('/');
					bld.append(path.toPortableString());
					urlPath = bld.toString();
				}
			}
			url = new URI(u.getScheme(), u.getAuthority(), urlPath, u.getQuery(), u.getFragment()).toURL();
		}
		catch(RuntimeException e)
		{
			throw e;
		}
		catch(Exception e)
		{
			throw new RuntimeException(e);
		}
		return url;
	}

	public static URL getParentURL(URL url)
	{
		if(url == null)
			return null;

		try
		{
			return getParentURI(url.toURI()).toURL();
		}
		catch(MalformedURLException e)
		{
			return null;
		}
		catch(URISyntaxException e)
		{
			return null;
		}
	}

	public static URI getParentURI(URI uri)
	{
		if(uri == null)
			return uri;

		IPath uriPath = Path.fromPortableString(uri.getPath());
		if(uriPath.segmentCount() == 0)
			return null;

		uriPath = uriPath.removeLastSegments(1).addTrailingSeparator();
		try
		{
			return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
					uriPath.toPortableString(), uri.getQuery(), uri.getFragment());
		}
		catch(URISyntaxException e)
		{
			// Shouldn't happen since we started with a valid URI
			//
			return null;
		}
	}

	public static boolean isLocalURL(URL url)
	{
		String proto = url.getProtocol();
		if(proto.equals("jar") || proto.equals("reference"))
		{
			String spec = url.getFile();
			int sepIdx = spec.indexOf(':');
			if(sepIdx == -1)
				return false;
			proto = spec.substring(0, sepIdx);
		}
		return "file".equals(proto) || "platform".equals(proto) || proto.startsWith("bundle");
	}

	public static URL normalizeToURL(String surl) throws MalformedURLException
	{
		if(surl == null)
			return null;

		try
		{
			return new URL(surl);
		}
		catch(MalformedURLException e)
		{
			// Do a space check.
			//
			if(surl.indexOf(' ') > 0)
			{
				try
				{
					return new URL(surl.replaceAll("\\s", "%20"));
				}
				catch(MalformedURLException me1)
				{
				}
			}

			try
			{
				return new File(surl).toURI().toURL();
			}
			catch(MalformedURLException me2)
			{
				// Throw the original exception
				//
				throw e;
			}
		}
	}

	public static URI normalizeToURI(String repository, boolean asFolder) throws CoreException
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

		boolean change = false;
		String path = uri.getPath();
		if(asFolder && !path.endsWith("/"))
		{
			path += "/";
			change = true;
		}

		String scheme = uri.getScheme();
		if(scheme == null)
		{
			scheme = "file";
			change = true;
		}

		try
		{
			if(change)
				uri = new URI(scheme, uri.getAuthority(), path, uri.getQuery(), uri.getFragment());
			return uri;
		}
		catch(URISyntaxException e)
		{
			throw BuckminsterException.wrap(e);
		}
	}

	public static URL resolveURL(URL contextURL, String url)
	{
		if(url == null)
			return null;

		try
		{
			if(contextURL == null)
				return normalizeToURL(url);
			return new URL(contextURL, url);
		}
		catch(MalformedURLException e)
		{
			return null;
		}
	}
}
