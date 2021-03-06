/*****************************************************************************
 * Copyright (c) 2006-2013, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.core.metadata.model;

import java.util.UUID;

import org.eclipse.buckminster.core.query.model.ComponentQuery;
import org.eclipse.buckminster.sax.AbstractSaxableElement;
import org.eclipse.buckminster.sax.ISaxableElement;
import org.eclipse.buckminster.sax.UUIDKeyed;
import org.eclipse.buckminster.sax.Utils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @author Thomas Hallgren
 * 
 */
public class IDWrapper extends AbstractSaxableElement implements Comparable<IDWrapper> {
	public static final String TAG = "idwrapper"; //$NON-NLS-1$

	public static final String ATTR_ID = "id"; //$NON-NLS-1$

	private final UUID id;

	private final UUIDKeyed wrapped;

	public IDWrapper(UUID id, UUIDKeyed wrapped) {
		this.id = id;
		this.wrapped = wrapped;
	}

	public IDWrapper(UUIDKeyed wrapped) {
		this(wrapped.getId(), wrapped);
	}

	@Override
	public int compareTo(IDWrapper o) {
		return id.compareTo(o.id);
	}

	@Override
	public String getDefaultTag() {
		return TAG;
	}

	public UUID getId() {
		return id;
	}

	public UUIDKeyed getWrapped() {
		return wrapped;
	}

	@Override
	protected void addAttributes(AttributesImpl attrs) throws SAXException {
		Utils.addAttribute(attrs, ATTR_ID, id.toString());
	}

	@Override
	protected void emitElements(ContentHandler receiver, String namespace, String prefix) throws SAXException {
		if (wrapped instanceof BillOfMaterials)
			((BillOfMaterials) wrapped).wrappedToSax(receiver, namespace, prefix, ((ISaxableElement) wrapped).getDefaultTag());
		else if (wrapped instanceof ComponentQuery) {
			// Relative paths must be resolved prior to inclusion
			//
			ComponentQuery query = ((ComponentQuery) wrapped).resolve();
			query.toSax(receiver, namespace, prefix, query.getDefaultTag());
		} else
			((ISaxableElement) wrapped).toSax(receiver, namespace, prefix, ((ISaxableElement) wrapped).getDefaultTag());
	}
}
