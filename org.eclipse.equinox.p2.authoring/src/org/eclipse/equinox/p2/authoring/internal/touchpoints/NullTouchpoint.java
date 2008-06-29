/*******************************************************************************
 * Copyright (c) 2008
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed below, as Initial Contributors under such license.
 * The text of such license is available at 
 * http://www.eclipse.org/legal/epl-v10.html.
 * 
 * Contributors:
 * 		Henrik Lindberg
 *******************************************************************************/

package org.eclipse.equinox.p2.authoring.internal.touchpoints;

import org.eclipse.equinox.p2.authoring.spi.ITouchpointInstructionDescriptor;
import org.eclipse.equinox.p2.authoring.spi.ITouchpointTypeDescriptor;
import org.eclipse.equinox.p2.authoring.spi.TouchpointInstruction;

/**
 * Descriptor class for the p2 NULL touchpoint. The description is used by
 * p2 meta data authoring to configure forms, provide validation and lookup.
 * 
 * @author Henrik Lindberg
 *
 */
public final class NullTouchpoint implements ITouchpointTypeDescriptor
{
	public static ITouchpointInstructionDescriptor[] s_types = new TouchpointInstruction[0]; 

	/**
	 * Returns the instructions for the native touchpoint version 1.0.0
	 */
	public ITouchpointInstructionDescriptor[] getInstructions()
	{
		return s_types;
	}

	/**
	 * Returns "none" - a special id string - should not be used as an id string in a IU.
	 */
	public String getTypeId()
	{
		return "null"; //$NON-NLS-1$
	}

	/**
	 * Returns "0.0.0".
	 */
	public String getVersionString()
	{
		return "0.0.0"; //$NON-NLS-1$
	}
	public boolean isNull()
	{
		return true;
	}
	public boolean isUnknown()
	{
		return false;
	}

}