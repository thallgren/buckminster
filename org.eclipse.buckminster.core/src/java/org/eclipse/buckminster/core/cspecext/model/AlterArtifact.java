/*****************************************************************************
 * Copyright (c) 2006-2013, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.core.cspecext.model;

import java.util.Map;
import java.util.Set;

import org.eclipse.buckminster.core.cspec.IArtifact;
import org.eclipse.buckminster.core.cspec.builder.ArtifactBuilder;
import org.eclipse.buckminster.core.cspec.builder.TopLevelAttributeBuilder;
import org.eclipse.buckminster.core.cspec.model.Artifact;
import org.eclipse.buckminster.core.cspec.model.CSpec;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

/**
 * @author Thomas Hallgren
 */
public class AlterArtifact extends AlterAttribute<Artifact> {
	public static final String ELEM_REMOVE_PATH = "removePath"; //$NON-NLS-1$

	private final Set<IPath> removedPaths;

	public AlterArtifact(Artifact base, Set<String> removedHints, Map<String, String> alteredHints, Set<IPath> removedPaths) {
		super(base, removedHints, alteredHints);
		this.removedPaths = CSpec.createUnmodifiablePaths(removedPaths);
	}

	@Override
	public void alterAttribute(TopLevelAttributeBuilder original) throws CoreException {
		if (!(original instanceof ArtifactBuilder))
			throw BuckminsterException.fromMessage("%s is not an artifact", original.getQualifiedName()); //$NON-NLS-1$

		IArtifact base = getBase();
		ArtifactBuilder aBld = (ArtifactBuilder) original;
		alterPaths(aBld);
		alterDocumentation(aBld);
		aBld.setBase(CSpecExtension.overrideCheckNull(aBld.getBase(), base.getBase()));
	}

	protected void alterPaths(ArtifactBuilder original) throws CoreException {
		alterPaths(original.getCSpecName(), original.getName(), original.getPaths(), this.getBase().getPaths(), removedPaths);
	}
}
