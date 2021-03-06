/*******************************************************************************
 * Copyright (c) 2004, 2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/

package org.eclipse.buckminster.subclipse.internal;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.eclipse.buckminster.core.CorePlugin;
import org.eclipse.buckminster.core.RMContext;
import org.eclipse.buckminster.core.version.VersionSelector;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.Logger;
import org.eclipse.buckminster.runtime.MonitorUtils;
import org.eclipse.buckminster.subclipse.Messages;
import org.eclipse.buckminster.subversion.GenericCache;
import org.eclipse.buckminster.subversion.GenericSession;
import org.eclipse.buckminster.subversion.ISubversionCache;
import org.eclipse.buckminster.subversion.ISvnEntryHelper;
import org.eclipse.buckminster.subversion.RepositoryAccess;
import org.eclipse.buckminster.subversion.SvnExceptionHandler;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.tigris.subversion.clientadapter.Activator;
import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.subclipse.core.SVNClientManager;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.SVNProviderPlugin;
import org.tigris.subversion.subclipse.core.repo.SVNRepositories;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.ISVNDirEntry;
import org.tigris.subversion.svnclientadapter.ISVNInfo;
import org.tigris.subversion.svnclientadapter.ISVNPromptUserPassword;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNRevision;
import org.tigris.subversion.svnclientadapter.SVNUrl;

/**
 * <p>
 * The SVN repository will be able to use reader checks if a repository contains
 * the three recommended directories <code>trunk</code>, <code>tags</code>, and
 * <code>branches</code>. A missing <code>tags</code> directory is interpreted
 * as no <code>tags</code>. A missing <code>branches</code> directory is
 * interpreted as no branches. In order to use <code>trunk</code>,
 * <code>tags</code>, and <code>branches</code> repository identifier must
 * contain the path element <code>trunk</code>. Anything that follows the
 * <code>trunk</code> element in the path will be considered a
 * <code>module</code> path. If no <code>trunk</code> element is present in the
 * path, the last element will be considered the <code>module</code>
 * </p>
 * <p>
 * The repository URL may also contain a query part that in turn may have four
 * different flags:
 * <dl>
 * <dt>moduleBeforeTag</dt>
 * <dd>When resolving a tag, put the module name between the <code>tags</code>
 * directory and the actual tag</dd>
 * <dt>moduleAfterTag</dt>
 * <dd>When resolving a tag, append the module name after the actual tag</dd>
 * <dt>moduleBeforeBranch</dt>
 * <dd>When resolving a branch, put the module name between the
 * <code>branches</code> directory and the actual branch</dd>
 * <dt>moduleAfterBranch</dt>
 * <dd>When resolving a branch, append the module name after the actual branch</dd>
 * </dl>
 * </p>
 * A fragment in the repository URL will be treated as a sub-module. It will be
 * appended at the end of the resolved URL.
 * 
 * @author Thomas Hallgren
 * @author Guillaume Chatelet
 */
public class SvnSession extends GenericSession<ISVNRepositoryLocation, ISVNDirEntry, SVNRevision> {

	private class UnattendedPromptUserPassword implements ISVNPromptUserPassword {
		private int promptPasswordLimit = 3;

		private int promptUserLimit = 3;

		@Override
		public String askQuestion(String realm, String question, boolean showAnswer, boolean maySave) {
			// We do not support questions
			//
			return null;
		}

		@Override
		public int askTrustSSLServer(String info, boolean allowPermanently) {
			return ISVNPromptUserPassword.AcceptTemporary;
		}

		@Override
		public boolean askYesNo(String realm, String question, boolean yesIsDefault) {
			return yesIsDefault;
		}

		@Override
		public String getPassword() {
			return password;
		}

		@Override
		public int getSSHPort() {
			// We do not support SSH
			//
			return -1;
		}

		@Override
		public String getSSHPrivateKeyPassphrase() {
			// We do not support SSH
			//
			return null;
		}

		@Override
		public String getSSHPrivateKeyPath() {
			// We do not support SSH
			//
			return null;
		}

		@Override
		public String getSSLClientCertPassword() {
			// We do not support SSL
			//
			return null;
		}

		@Override
		public String getSSLClientCertPath() {
			// We do not support SSL
			//
			return null;
		}

		@Override
		public String getUsername() {
			return username;
		}

		@Override
		public boolean prompt(String realm, String user, boolean maySave) {
			// We support the password prompt only if we actually know the
			// password
			// and only a limited number of times
			//
			return password != null && --promptPasswordLimit >= 0;
		}

		@Override
		public boolean promptSSH(String realm, String user, int sshPort, boolean maySave) {
			// We do not support SSH prompt
			//
			return false;
		}

		@Override
		public boolean promptSSL(String realm, boolean maySave) {
			// We do not support SSL prompt
			//
			return false;
		}

		@Override
		public boolean promptUser(String realm, String user, boolean maySave) {
			// We do support the user prompt but only a limited number of times
			//
			return --promptUserLimit >= 0;
		}

		@Override
		public boolean userAllowedSave() {
			// No need to save anything
			//
			return false;
		}
	}

	private static final SvnEntryHelper HELPER = new SvnEntryHelper();

	private static final ISVNDirEntry[] emptyFolder = new ISVNDirEntry[0];

	private static SVNProviderPlugin getPlugin() {
		return SVNProviderPlugin.getPlugin();
	}

	private ISVNClientAdapter clientAdapter;

	private static final String UNKNOWN_ROOT_PREFIX = SvnSession.class.getPackage().getName() + ".root."; //$NON-NLS-1$

	private static SVNRepositories getRepositories() {
		return getPlugin().getRepositories();
	}

	/**
	 * @param repositoryURI
	 *            The string representation of the URI that appoints the trunk
	 *            of repository module. No branch or tag information must be
	 *            included.
	 * @param branch
	 *            The desired branch or <code>null</code> if not applicable.
	 * @param tag
	 *            The desired tag or <code>null</code> if not applicable.
	 * @param revision
	 *            The desired revision or <code>-1</code> of not applicable
	 * @param timestamp
	 *            The desired timestamp or <code>null</code> if not applicable
	 * @param context
	 *            The context used for the resolution/materialization operation
	 * @throws CoreException
	 */
	public SvnSession(String repositoryURI, VersionSelector branchOrTag, long revision, Date timestamp, RMContext context) throws CoreException {
		super(repositoryURI, branchOrTag, revision, timestamp, context);
	}

	@Override
	public void close() {
		clientAdapter.dispose();
	}

	@Override
	public ISVNRepositoryLocation[] getKnownRepositories() throws CoreException {
		return getRepositories().getKnownRepositories(new NullProgressMonitor());
	}

	@Override
	public long getLastChangeNumber() throws CoreException {
		try {
			SVNUrl svnURL = TypeTranslator.from(getSVNUrl(null));
			ISVNDirEntry root = clientAdapter.getDirEntry(svnURL, getRevision());
			if (root == null)
				throw new FileNotFoundException(svnURL.toString());
			return root.getLastChangedRevision().getNumber();
		} catch (Exception e) {
			throw BuckminsterException.wrap(e);
		}
	}

	public long getLastChangeNumber(File workingCopy) throws CoreException {
		try {
			return clientAdapter.getInfoFromWorkingCopy(workingCopy).getLastChangedRevision().getNumber();
		} catch (Exception e) {
			throw BuckminsterException.wrap(e);
		}
	}

	@Override
	public Date getLastTimestamp() throws CoreException {
		try {
			SVNUrl svnURL = TypeTranslator.from(getSVNUrl(null));
			ISVNDirEntry root = clientAdapter.getDirEntry(svnURL, getRevision());
			if (root == null)
				throw new FileNotFoundException(svnURL.toString());
			return root.getLastChangedDate();
		} catch (Exception e) {
			throw BuckminsterException.wrap(e);
		}
	}

	public SVNRevision.Number getRepositoryRevision(IProgressMonitor monitor) throws CoreException {
		SVNRevision.Number repoRev = null;

		if (getRevision() instanceof SVNRevision.Number) {
			repoRev = (SVNRevision.Number) getRevision();
			MonitorUtils.complete(monitor);
		} else {
			monitor.beginTask(null, 1);
			try {
				for (int retries = 0;; ++retries) {
					try {
						SVNUrl svnURL = TypeTranslator.from(getSVNUrl(null));
						ISVNInfo info = clientAdapter.getInfo(svnURL);
						if (info == null)
							return null;
						repoRev = info.getRevision();
						break;
					} catch (SVNClientException e) {
						if (++retries < 3) {
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e1) {
							}
							continue;
						}
						throw BuckminsterException.wrap(e);
					}
				}
			} finally {
				monitor.done();
			}
		}
		return repoRev;
	}

	@Override
	public ISVNDirEntry getRootEntry(IProgressMonitor monitor) throws CoreException {
		// Synchronizing on an interned string should make it impossible for two
		// sessions to request the same entry from the remote server
		//
		SVNUrl url = TypeTranslator.from(getSVNUrl(null));
		SVNUrl parent = url.getParent();
		if (parent != null) {
			// List the parent instead of fetching the folder explicitly. This
			// will save us a lot of calls since the list is cached.
			//
			String lastEntry = url.getLastPathSegment();
			ISVNDirEntry[] dirEntries;
			try {
				dirEntries = innerListFolder(TypeTranslator.from(url.getParent()), monitor);
			}

			catch (CoreException e) {
				dirEntries = emptyFolder;
			}
			for (ISVNDirEntry dirEntry : dirEntries)
				if (dirEntry.getPath().equals(lastEntry))
					return dirEntry;

			// Parent was not accessible. Perhaps we have no permissions.
		}

		SVNRevision revision = getRevision();
		String key = GenericCache.cacheKey(TypeTranslator.from(url), getRevision()).intern();
		synchronized (key) {
			// Check the cache. We use containsKey since it might have
			// valid null entries
			//
			if (getCache().dirContainsKey(key))
				return getCache().getDir(key);

			Logger logger = CorePlugin.getLogger();
			monitor.beginTask(null, 1);
			try {
				logger.debug("Obtaining remote folder %s[%s]", url, revision); //$NON-NLS-1$
				ISVNDirEntry entry = getClientAdapter().getDirEntry(url, revision);
				getCache().putDir(key, entry);
				return entry;
			} catch (SVNClientException e) {
				if (SvnExceptionHandler.hasSvnException(e)) {
					logger.debug("Remote folder does not exist %s[%s]", url, revision); //$NON-NLS-1$
					getCache().putDir(key, null);
					return null;
				}
				throw BuckminsterException.wrap(e);
			}
		}
	}

	@Override
	public ISvnEntryHelper<ISVNDirEntry> getSvnEntryHelper() {
		return HELPER;
	}

	@Override
	public SVNRevision getSVNRevision(long revision, Date timestamp) {
		if (revision == -1) {
			if (timestamp == null)
				return SVNRevision.HEAD;

			return new SVNRevision.DateSpec(timestamp);
		}
		if (timestamp != null)
			throw new IllegalArgumentException(org.eclipse.buckminster.subversion.Messages.svn_session_cannot_use_both_timestamp_and_revision_number);
		return new SVNRevision.Number(revision);
	}

	@Override
	public String toString() {
		try {
			return getSVNUrl(null).toString();
		} catch (CoreException e) {
			return super.toString();
		}
	}

	@Override
	protected void createRoots(Collection<RepositoryAccess> sourceRoots) throws CoreException {
		SVNRepositories repos = getRepositories();
		for (RepositoryAccess root : sourceRoots) {
			Properties configuration = new Properties();
			configuration.setProperty("url", root.getSvnURL().toString()); //$NON-NLS-1$
			String user = root.getUser();
			if (user != null)
				configuration.setProperty("user", user); //$NON-NLS-1$
			String pwd = root.getPassword();
			if (pwd != null)
				configuration.setProperty("password", pwd); //$NON-NLS-1$

			try {
				final ISVNRepositoryLocation repoLocation = repos.createRepository(configuration);
				repos.addOrUpdateRepository(repoLocation);
			} catch (SVNException e) {
				// Repository already exists
			}
		}
	}

	@Override
	protected ISubversionCache<ISVNDirEntry> getCache(Map<UUID, Object> userCache) {
		assert (cache == null);
		final SvnCache svnCache = new SvnCache();
		svnCache.initialize(userCache);
		return svnCache;
	}

	@Override
	protected ISVNDirEntry[] getEmptyEntryList() {
		return emptyFolder;
	}

	@Override
	protected String getRootUrl(ISVNRepositoryLocation location) {
		return location.getRepositoryRoot().toString();
	}

	@Override
	protected String getUnknownRootPrefix() {
		return UNKNOWN_ROOT_PREFIX;
	}

	@Override
	protected void initializeSvn(RMContext context, URI ourRoot, ISVNRepositoryLocation bestMatch) throws CoreException {
		final SVNProviderPlugin plugin = getPlugin();
		final ISVNClientAdapter client = getClientAdapter();

		// Add the UnattendedPromptUserPassword callback only in case
		// the authentication data (at least the username) is actually
		// specified in the URL
		//
		ISVNPromptUserPassword pwCb = (username == null) ? plugin.getSvnPromptUserPassword() : new UnattendedPromptUserPassword();

		if (pwCb != null)
			client.addPasswordCallback(pwCb);

		clientAdapter = client;
		if (bestMatch == null)
			addUnknownRoot(context.getBindingProperties(), new RepositoryAccess(ourRoot, username, password));
	}

	@Override
	protected ISVNDirEntry[] innerListFolder(URI url, IProgressMonitor monitor) throws CoreException {
		monitor.beginTask(null, 1);
		try {
			return clientAdapter.getList(TypeTranslator.from(url), getRevision(), getRevision(), false);
		} catch (SVNClientException e) {
			throw BuckminsterException.wrap(e);
		} finally {
			monitor.worked(1);
		}
	}

	ISVNClientAdapter getClientAdapter() throws CoreException {
		if (clientAdapter == null) {
			final SVNClientManager clientManager = getPlugin().getSVNClientManager();
			clientAdapter = Activator.getDefault().getClientAdapter(clientManager.getSvnClientInterface());
			if (clientAdapter == null)
				clientAdapter = Activator.getDefault().getAnyClientAdapter();
			if (clientAdapter == null)
				throw BuckminsterException.fromMessage(Messages.unable_to_load_default_svn_client);
		}
		return clientAdapter;
	}

	private SvnCache getCache() {
		return ((SvnCache) cache);
	}
}
