/**
 * <copyright>
 * </copyright>
 *
 * $Id$
 */
package org.eclipse.buckminster.mspec.provider;

import org.eclipse.buckminster.model.common.provider.CommonEditPlugin;

import org.eclipse.emf.common.EMFPlugin;

import org.eclipse.emf.common.util.ResourceLocator;

/**
 * This is the central singleton for the Mspec edit plugin. <!-- begin-user-doc
 * --> <!-- end-user-doc -->
 * 
 * @generated
 */
public final class MspecEditPlugin extends EMFPlugin {
	/**
	 * The actual implementation of the Eclipse <b>Plugin</b>. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public static class Implementation extends EclipsePlugin {
		/**
		 * Creates an instance. <!-- begin-user-doc --> <!-- end-user-doc -->
		 * 
		 * @generated
		 */
		public Implementation() {
			super();

			// Remember the static instance.
			//
			plugin = this;
		}
	}

	/**
	 * Keep track of the singleton. <!-- begin-user-doc --> <!-- end-user-doc
	 * -->
	 * 
	 * @generated
	 */
	public static final MspecEditPlugin INSTANCE = new MspecEditPlugin();

	/**
	 * Keep track of the singleton. <!-- begin-user-doc --> <!-- end-user-doc
	 * -->
	 * 
	 * @generated
	 */
	private static Implementation plugin;

	/**
	 * Returns the singleton instance of the Eclipse plugin. <!-- begin-user-doc
	 * --> <!-- end-user-doc -->
	 * 
	 * @return the singleton instance.
	 * @generated
	 */
	public static Implementation getPlugin() {
		return plugin;
	}

	/**
	 * Create the instance. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public MspecEditPlugin() {
		super(new ResourceLocator[] { CommonEditPlugin.INSTANCE, });
	}

	/**
	 * Returns the singleton instance of the Eclipse plugin. <!-- begin-user-doc
	 * --> <!-- end-user-doc -->
	 * 
	 * @return the singleton instance.
	 * @generated
	 */

	@Override
	public ResourceLocator getPluginResourceLocator() {
		return plugin;
	}

}
