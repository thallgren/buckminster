/*******************************************************************************
 * Copyright (c) 2004, 2005
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/
package org.eclipse.buckminster.cvspkg.internal;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.internal.ccvs.core.CVSMessages;
import org.eclipse.team.internal.ccvs.core.CVSStatus;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.client.CommandOutputListener;

/**
 * An RLogListener that collects global meta-data about the repository. The
 * following data is collected
 * <ul>
 * <li>The timestamp of the last modified revision</li>
 * <li>All tag names, i.e. tags that are not associated with a branch revision</li>
 * <li>All branch names, i.e. tags that are associated with a branch revision</li>
 * </ul>
 */
@SuppressWarnings("restriction")
public class MetaDataCollector extends CommandOutputListener {
	private static final int BEGIN = 0;

	private static final int HEADER = 1;

	private static final int REVISION = 3;

	private static final int NEXT_REV_OR_BEGIN = 4;

	private static final String LOG_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss zzz";//$NON-NLS-1$

	// A new format for log dates was introduced in 1.12.9
	//
	private static final String LOG_TIMESTAMP_FORMAT_OLD = "yyyy/MM/dd HH:mm:ss zzz";//$NON-NLS-1$

	private static final Locale LOG_TIMESTAMP_LOCALE = Locale.US;

	// Server message prefix used for error detection
	//
	private static final String NOTHING_KNOWN_ABOUT = "nothing known about "; //$NON-NLS-1$

	private static final Pattern revDataExpr = Pattern.compile(
			"^date:\\s*([^;]+);\\s*author:[^;]+;\\s*state:\\s*([^;]+);.*$", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

	private static final int SYMBOLIC_NAMES = 2;

	/**
	 * Converts a time stamp as sent from a cvs server for a "log" command into
	 * a <code>Date</code>.
	 */
	private static Date convertFromLogTime(String modTime) {
		if (modTime == null)
			return null;

		String timestampFormat = LOG_TIMESTAMP_FORMAT;
		// Compatibility for older cvs version (pre 1.12.9)
		if (modTime.length() > 4 && modTime.charAt(4) == '/')
			timestampFormat = LOG_TIMESTAMP_FORMAT_OLD;

		SimpleDateFormat format = new SimpleDateFormat(timestampFormat, LOG_TIMESTAMP_LOCALE);
		try {
			return format.parse(modTime);
		} catch (ParseException e) {
			// fallback is to return null
			return null;
		}
	}

	/**
	 * branch tags have odd number of segments or have an even number with a
	 * zero as the second last segment e.g: 1.1.1, 1.26.0.2 are branch revision
	 * numbers
	 */
	private static boolean isBranchTag(String tagName) {
		// First check if we have an odd number of segments (i.e. even number of
		// dots)
		//
		int numberOfDots = 0;
		int lastDot = 0;
		int top = tagName.length();
		for (int i = 0; i < top; i++) {
			if (tagName.charAt(i) == '.') {
				numberOfDots++;
				lastDot = i;
			}
		}
		if ((numberOfDots % 2) == 0)
			return true;
		if (numberOfDots == 1)
			return false;

		// If not, check if the second lat segment is a zero
		//
		return tagName.charAt(lastDot - 1) == '0' && tagName.charAt(lastDot - 2) == '.';
	}

	private final HashSet<String> branches = new HashSet<String>();

	private final HashSet<String> tags = new HashSet<String>();

	private Date lastModificationTime;

	private int state = BEGIN;

	@Override
	public IStatus errorLine(String line, ICVSRepositoryLocation location, ICVSFolder commandRoot, IProgressMonitor monitor) {
		String serverMessage = getServerMessage(line, location);
		if (serverMessage != null && serverMessage.startsWith(NOTHING_KNOWN_ABOUT))
			return new CVSStatus(IStatus.ERROR, CVSStatus.DOES_NOT_EXIST, NLS.bind(CVSMessages.CVSStatus_messageWithRoot, new String[] {
					commandRoot.getName(), line }), (Throwable) null);
		return OK;
	}

	/**
	 * Returns the names of all branches found in this repository
	 * 
	 * @return All known branch names.
	 */
	public Set<String> getBranchNames() {
		return branches;
	}

	public Date getLastModificationTime() {
		return lastModificationTime;
	}

	/**
	 * Returns the names of all tags found in this repository
	 * 
	 * @return All known tag names.
	 */
	public Set<String> getTagNames() {
		return tags;
	}

	@Override
	public IStatus messageLine(String line, ICVSRepositoryLocation location, ICVSFolder commandRoot, IProgressMonitor monitor) {
		switch (state) {
			case BEGIN:
				if (line.startsWith("RCS file:")) //$NON-NLS-1$
					state = HEADER;
				break;
			case NEXT_REV_OR_BEGIN:
			case HEADER:
				if (line.startsWith("RCS file:")) //$NON-NLS-1$
					state = HEADER;
				else if (line.startsWith("revision ")) //$NON-NLS-1$
					state = REVISION;
				else if (line.startsWith("symbolic names:")) //$NON-NLS-1$
					state = SYMBOLIC_NAMES;
				break;
			case SYMBOLIC_NAMES:
				if (line.startsWith("keyword substitution:")) //$NON-NLS-1$
					state = HEADER;
				else
					this.symbolicName(line);
				break;
			case REVISION:
				this.revision(line, location);
				break;
		}
		return OK;
	}

	private void revision(String line, ICVSRepositoryLocation location) {
		Matcher matcher = revDataExpr.matcher(line);
		if (matcher.matches()) {
			Date date = convertFromLogTime(matcher.group(1) + " GMT"); //$NON-NLS-1$
			if (lastModificationTime == null || lastModificationTime.compareTo(date) < 0)
				lastModificationTime = date;
		}
		state = NEXT_REV_OR_BEGIN;
	}

	private void symbolicName(String line) {
		int firstColon = line.indexOf(':');
		String tag = line.substring(1, firstColon);
		String rev = line.substring(firstColon + 2);
		if (isBranchTag(rev))
			branches.add(tag);
		else
			tags.add(tag);
	}
}
