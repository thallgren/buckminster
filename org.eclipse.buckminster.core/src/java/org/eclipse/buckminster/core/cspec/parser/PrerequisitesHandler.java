/*****************************************************************************
 * Copyright (c) 2006-2013, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.core.cspec.parser;

import org.eclipse.buckminster.core.cspec.builder.ActionBuilder;
import org.eclipse.buckminster.core.cspec.builder.TopLevelAttributeBuilder;
import org.eclipse.buckminster.core.cspec.model.Prerequisite;
import org.eclipse.buckminster.core.cspec.model.Prerequisites;
import org.eclipse.buckminster.sax.AbstractHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * @author Thomas Hallgren
 */
public class PrerequisitesHandler extends GroupHandler {
	public static final String TAG = Prerequisites.TAG;

	public PrerequisitesHandler(AbstractHandler parent) {
		super(parent, false);
	}

	@Override
	protected TopLevelAttributeBuilder createAttributeBuilder() {
		return getActionBuilder().getPrerequisitesBuilder();
	}

	@Override
	protected String getNameAttribute(Attributes attrs) throws SAXException {
		return getOptionalStringValue(attrs, Prerequisite.ATTR_ALIAS);
	}

	private ActionBuilder getActionBuilder() {
		return (ActionBuilder) ((IAttributeBuilderSupport) this.getParentHandler()).getAttributeBuilder();
	}
}
