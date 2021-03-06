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

import org.eclipse.equinox.p2.authoring.internal.InstallableUnitBuilder.TouchpointTypeBuilder;
import org.eclipse.equinox.p2.authoring.spi.ITouchpointActionDescriptor;
import org.eclipse.equinox.p2.authoring.spi.ITouchpointTypeDescriptor;
import org.eclipse.equinox.p2.authoring.spi.TouchpointActionDescriptor;
import org.eclipse.equinox.p2.authoring.spi.TouchpointTypeDescriptor;

/**
 * Descriptor class for the p2 UNKNOWN touchpoint. The description is used by
 * p2 meta data authoring to configure forms, provide validation and lookup.
 * 
 * This class is used to hold the original touchpoint type and version found in an IU
 * when p2 authoring does not have a description of the touchpoint type.
 * 
 * This instance reports the type as "unknown", but the original type and version can be
 * obtained via the methods {@ #getOriginalTypeId()} and {@link #getOriginalVersion()}.
 * 
 * @author Henrik Lindberg
 *
 */
public final class UnknownTouchpoint extends TouchpointTypeDescriptor implements ITouchpointTypeDescriptor
{
	public static ITouchpointActionDescriptor[] s_types = new TouchpointActionDescriptor[0]; 
	private String m_originalTypeId;
	private String m_originalVersion;
	
	public String getOriginalTypeId()
	{
		return m_originalTypeId;
	}

	public String getOriginalVersion()
	{
		return m_originalVersion;
	}
	/**
	 * Creates an unknown touchpoint type descriptor for a type.
	 * @param originalTouchpointType
	 */
	public UnknownTouchpoint(TouchpointTypeBuilder originalTouchpointType)
	{
		m_originalTypeId = originalTouchpointType.getTypeid();
		m_originalVersion = originalTouchpointType.getVersion();
	}

	/**
	 * Returns the instructions for the unknown touchpoint (none)
	 */
	public ITouchpointActionDescriptor[] getActions()
	{
		return s_types;
	}

	/**
	 * Returns "unknown" - a special id string - should not be used as an id string in a IU.
	 */
	public String getTypeId()
	{
		return "unknown"; //$NON-NLS-1$
	}

	/**
	 * Returns "".
	 */
	public String getVersionString()
	{
		return ""; //$NON-NLS-1$
	}
	@Override
	public boolean isUnknown()
	{
		return true;
	}

}
