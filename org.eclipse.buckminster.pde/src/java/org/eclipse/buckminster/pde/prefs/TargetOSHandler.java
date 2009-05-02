/*****************************************************************************
 * Copyright (c) 2006-2007, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.pde.prefs;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.internal.core.target.provisional.ITargetDefinition;
import org.osgi.service.prefs.BackingStoreException;

/**
 * Custom preference handler for the target operating system preference.
 * 
 * @author Thomas Hallgren
 */
@SuppressWarnings("restriction")
public class TargetOSHandler extends TargetVariableHandler
{
	@Override
	public void unset() throws BackingStoreException
	{
		set(null);
	}

	@Override
	protected String get(ITargetDefinition definition) throws CoreException
	{
		return definition.getOS();
	}

	@Override
	protected void set(ITargetDefinition definition, String value) throws CoreException
	{
		definition.setOS(value);
	}
}
