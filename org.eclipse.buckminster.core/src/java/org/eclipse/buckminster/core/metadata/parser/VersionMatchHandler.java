/*****************************************************************************
 * Copyright (c) 2006-2013, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.core.metadata.parser;

import java.util.Date;

import org.eclipse.buckminster.core.helpers.DateAndTimeUtils;
import org.eclipse.buckminster.core.parser.ExtensionAwareHandler;
import org.eclipse.buckminster.core.version.VersionHelper;
import org.eclipse.buckminster.core.version.VersionMatch;
import org.eclipse.buckminster.core.version.VersionSelector;
import org.eclipse.buckminster.sax.AbstractHandler;
import org.eclipse.equinox.p2.metadata.Version;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author Thomas Hallgren
 */
class VersionMatchHandler extends ExtensionAwareHandler {
	public static final String TAG = VersionMatch.TAG;

	private VersionMatch versionMatch;

	VersionMatchHandler(AbstractHandler parent) {
		super(parent);
	}

	@Override
	public void handleAttributes(Attributes attrs) throws SAXException {
		try {
			Version version = VersionHelper.parseVersionAttributes(attrs);
			VersionSelector branchOrTag = null;
			String tmp = getOptionalStringValue(attrs, VersionMatch.ATTR_BRANCH_OR_TAG);
			if (tmp != null)
				branchOrTag = VersionSelector.fromString(tmp);

			long revision = getOptionalLongValue(attrs, VersionMatch.ATTR_REVISION, -1L);

			Date timestamp = null;
			tmp = getOptionalStringValue(attrs, VersionMatch.ATTR_TIMESTAMP);
			if (tmp != null)
				timestamp = DateAndTimeUtils.fromISOFormat(tmp);

			String artifactInfo = getOptionalStringValue(attrs, VersionMatch.ATTR_ARTIFACT_INFO);
			versionMatch = new VersionMatch(version, branchOrTag, revision, timestamp, artifactInfo);
		} catch (Exception e) {
			throw new SAXParseException(e.getMessage(), getDocumentLocator(), e);
		}
	}

	VersionMatch getVersionMatch() {
		return versionMatch;
	}
}
