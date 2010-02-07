/*****************************************************************************
 * Copyright (c) 2006-2008, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *****************************************************************************/
package org.eclipse.buckminster.core.ctype;

import org.eclipse.buckminster.core.Messages;
import org.eclipse.buckminster.core.helpers.LocalizedException;
import org.eclipse.osgi.util.NLS;

/**
 * @author Thomas Hallgren
 */
public class ComponentTypeMismatchException extends LocalizedException {
	private static final long serialVersionUID = 5479744816736527579L;

	public ComponentTypeMismatchException(String componentName, String expectedType, String actualType) {
		super(NLS.bind(Messages.Component_type_mismatch_exception_for_component_0_Expected_1_but_actual_2, new Object[] { componentName,
				expectedType, actualType }));
	}
}
