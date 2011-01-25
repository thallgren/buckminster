/**
 * <copyright>
 * </copyright>
 *
 * $Id$
 */
package org.eclipse.buckminster.rmap.maven;

import org.eclipse.buckminster.rmap.Provider;
import org.eclipse.core.runtime.CoreException;

/**
 * <!-- begin-user-doc --> A representation of the model object '
 * <em><b>Maven Provider</b></em>'. <!-- end-user-doc -->
 * 
 * <p>
 * The following features are supported:
 * <ul>
 * <li>{@link org.eclipse.buckminster.rmap.maven.MavenProvider#getMappings <em>
 * Mappings</em>}</li>
 * </ul>
 * </p>
 * 
 * @see org.eclipse.buckminster.rmap.maven.MavenPackage#getMavenProvider()
 * @model
 * @generated
 */
public interface MavenProvider extends Provider {
	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @model
	 * @generated
	 */
	String getComponentName(String groupId, String artifactId);

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @model exceptions="org.eclipse.buckminster.model.common.CoreException"
	 * @generated
	 */
	MapEntry getMapEntry(String name) throws CoreException;

	/**
	 * Returns the value of the '<em><b>Mappings</b></em>' containment
	 * reference. <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Mappings</em>' containment reference isn't
	 * clear, there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * 
	 * @return the value of the '<em>Mappings</em>' containment reference.
	 * @see #setMappings(Mappings)
	 * @see org.eclipse.buckminster.rmap.maven.MavenPackage#getMavenProvider_Mappings()
	 * @model containment="true" extendedMetaData=
	 *        "name='mappings' kind='element' namespace='##targetNamespace'"
	 * @generated
	 */
	Mappings getMappings();

	/**
	 * Sets the value of the '
	 * {@link org.eclipse.buckminster.rmap.maven.MavenProvider#getMappings
	 * <em>Mappings</em>}' containment reference. <!-- begin-user-doc --> <!--
	 * end-user-doc -->
	 * 
	 * @param value
	 *            the new value of the '<em>Mappings</em>' containment
	 *            reference.
	 * @see #getMappings()
	 * @generated
	 */
	void setMappings(Mappings value);

} // MavenProvider
