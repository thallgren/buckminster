/*****************************************************************************
 * Copyright (c) 2006-2007, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.core.actor;

import java.util.UUID;

import org.eclipse.buckminster.runtime.BuckminsterException;

/**
 * @author kolwing
 *
 */
public class NoSuchActionException extends BuckminsterException
{
	private static final long serialVersionUID = -1141237125275735952L;

	public NoSuchActionException(UUID componentId, String actionName)
	{
		super(componentId.toString() + " : " + actionName);
	}
}
