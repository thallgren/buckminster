/*******************************************************************************
 * Copyright (c) 2004, 2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/
package org.eclipse.buckminster.ui.internal;

import org.eclipse.buckminster.core.cspec.model.CSpec;
import org.eclipse.buckminster.core.version.IVersion;
import org.eclipse.buckminster.sax.ISaxable;
import org.eclipse.core.runtime.CoreException;

public class CSpecEditorInput extends SaxableEditorInput
{
	private final CSpec m_cspec;

	public CSpecEditorInput(CSpec cspec)
	{
		m_cspec = cspec;
	}

	@Override
	public boolean equals(Object other)
	{
		return other == this || (other instanceof CSpecEditorInput && ((CSpecEditorInput)other).m_cspec.equals(m_cspec));
	}

	public boolean exists()
	{
		return true;
	}

	public String getName()
	{
		StringBuilder bld = new StringBuilder();
		bld.append(m_cspec.getName());
		IVersion version = m_cspec.getVersion();
		if(version != null)
		{
			bld.append(':');
			bld.append(version);
		}
		bld.append(".cspec");
		return bld.toString();
	}

	public String getToolTipText()
	{
		return this.getName();
	}

	@Override
	public int hashCode()
	{
		return m_cspec.hashCode();
	}

	@Override
	protected ISaxable getContent() throws CoreException
	{
		return m_cspec;
	}
}
