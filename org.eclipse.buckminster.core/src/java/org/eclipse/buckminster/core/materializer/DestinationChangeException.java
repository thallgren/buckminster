/*****************************************************************************
 * Copyright (c) 2006-2013, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.core.materializer;

import org.eclipse.buckminster.core.Messages;
import org.eclipse.buckminster.core.helpers.LocalizedException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.osgi.util.NLS;

/**
 * @author Thomas Hallgren
 * 
 */
public class DestinationChangeException extends LocalizedException {
	private static final long serialVersionUID = -7739997792455938561L;

	public DestinationChangeException(IPath fixedDest, IPath wantedDest) {
		super(NLS.bind(Messages.Attempt_to_change_fixed_materialization_location_0_to_1, fixedDest.toPortableString(), wantedDest.toPortableString()));
	}
}
