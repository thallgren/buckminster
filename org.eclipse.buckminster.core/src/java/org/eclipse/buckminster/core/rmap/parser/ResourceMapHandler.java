/*******************************************************************************
 * Copyright (c) 2004, 2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/

package org.eclipse.buckminster.core.rmap.parser;

import java.net.URL;

import org.eclipse.buckminster.core.common.model.ExpandingProperties;
import org.eclipse.buckminster.core.common.parser.DocumentationHandler;
import org.eclipse.buckminster.core.common.parser.PropertyManagerHandler;
import org.eclipse.buckminster.core.rmap.model.ResourceMap;
import org.eclipse.buckminster.sax.AbstractHandler;
import org.eclipse.buckminster.sax.ChildHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * @author Thomas Hallgren
 */
public class ResourceMapHandler extends PropertyManagerHandler {
	private URL contextURL;

	private SearchPathHandler searchPathHandler;

	private MatcherHandler.LocatorHandler locatorHandler;

	private MatcherHandler.RedirectHandler redirectHandler;

	private DocumentationHandler documentationHandler;

	private ResourceMap resourceMap;

	public ResourceMapHandler(AbstractHandler parent) {
		super(parent, ResourceMap.TAG);
	}

	@Override
	public void childPopped(ChildHandler child) throws SAXException {
		if (child == documentationHandler)
			getResourceMap().setDocumentation(documentationHandler.createDocumentation());
		else
			super.childPopped(child);
	}

	@Override
	public ChildHandler createHandler(String uri, String localName, Attributes attrs) throws SAXException {
		ChildHandler ch;
		if (SearchPathHandler.TAG.equals(localName)) {
			if (searchPathHandler == null)
				searchPathHandler = new SearchPathHandler(this);
			ch = searchPathHandler;
		} else if (MatcherHandler.LocatorHandler.TAG.equals(localName)) {
			if (locatorHandler == null)
				locatorHandler = new MatcherHandler.LocatorHandler(this);
			ch = locatorHandler;
		} else if (MatcherHandler.RedirectHandler.TAG.equals(localName)) {
			if (redirectHandler == null)
				redirectHandler = new MatcherHandler.RedirectHandler(this);
			ch = redirectHandler;
		} else if (DocumentationHandler.TAG.equals(localName)) {
			if (documentationHandler == null)
				documentationHandler = new DocumentationHandler(this);
			ch = documentationHandler;
		} else
			ch = super.createHandler(uri, localName, attrs);
		return ch;
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		super.endElement(uri, localName, qName);
		((ResourceMapParser) this.getTopHandler()).setResourceMap(this.getResourceMap());
	}

	@Override
	public ExpandingProperties<String> getProperties() {
		return (ExpandingProperties<String>) getResourceMap().getProperties();
	}

	public ResourceMap getResourceMap() {
		if (resourceMap == null)
			resourceMap = new ResourceMap(contextURL);
		return resourceMap;
	}

	@Override
	public void handleAttributes(Attributes attrs) throws SAXException {
		resourceMap = null;
	}

	void setContextURL(URL contextURL) {
		this.contextURL = contextURL;
	}
}
