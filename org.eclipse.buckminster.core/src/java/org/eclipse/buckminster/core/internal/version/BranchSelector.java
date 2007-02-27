/*******************************************************************************
 * Copyright (c) 2004, 2005
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 *******************************************************************************/
package org.eclipse.buckminster.core.internal.version;

import org.eclipse.buckminster.core.version.VersionSelectorType;

/**
 * An instance of this class represents the latest revision on a branch.
 * @author Thomas Hallgren
 */
public class BranchSelector extends QualifiedSelector
{
	public BranchSelector(String branchName, String typeName)
	{
		super(branchName, typeName);
	}

	@Override
	public final String getQualifier()
	{
		return TAG_LATEST;
	}

	public VersionSelectorType getType()
	{
		return VersionSelectorType.LATEST;
	}

	@Override
	void qualifierToString(StringBuilder bld)
	{
		bld.append('/');
		bld.append(TAG_LATEST);
	}
}
