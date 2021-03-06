/*****************************************************************************
 * Copyright (c) 2006-2013, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.core.metadata.parser;

import java.util.UUID;

import org.eclipse.buckminster.sax.UUIDKeyed;
import org.xml.sax.SAXException;

/**
 * @author Thomas Hallgren
 */
public interface IWrapperParent {
	UUIDKeyed getWrapped(UUID id) throws SAXException;
}
