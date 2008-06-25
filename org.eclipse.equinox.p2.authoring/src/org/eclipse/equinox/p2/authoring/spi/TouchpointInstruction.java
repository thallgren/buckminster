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

package org.eclipse.equinox.p2.authoring.spi;


/**
 * An implementaion of {@link ITouchpointInstructionDescriptor}.
 * @author Henrik Lindberg
 *
 */
public class TouchpointInstruction implements ITouchpointInstructionDescriptor
{
	private String m_key;
	private String m_label;
	
	private ITouchpointInstructionParameterDescriptor[] m_parameters;
	public TouchpointInstruction(String key, String label, ITouchpointInstructionParameterDescriptor[] parameters)
	{
		m_key = key;
		m_label = label;
		m_parameters = parameters;
	}
	public String getKey()
	{
		return m_key;
	}
	public String getLabel()
	{
		return m_label;
	}
	public ITouchpointInstructionParameterDescriptor[] getParameters()
	{
		return m_parameters;
	}

}
