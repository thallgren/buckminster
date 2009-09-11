/**
 * Copyright (c) 2006-2009, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *
 * $Id$
 */
package org.eclipse.buckminster.aggregator.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.buckminster.aggregator.AggregatorPackage;
import org.eclipse.buckminster.aggregator.InstallableUnitReference;
import org.eclipse.buckminster.aggregator.MappedRepository;
import org.eclipse.buckminster.aggregator.p2.InstallableUnit;
import org.eclipse.buckminster.aggregator.p2.MetadataRepository;
import org.eclipse.emf.common.notify.AdapterFactory;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.ResourceLocator;
import org.eclipse.emf.edit.provider.ComposeableAdapterFactory;
import org.eclipse.emf.edit.provider.IEditingDomainItemProvider;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.eclipse.emf.edit.provider.IItemPropertyDescriptor;
import org.eclipse.emf.edit.provider.IItemPropertySource;
import org.eclipse.emf.edit.provider.IStructuredItemContentProvider;
import org.eclipse.emf.edit.provider.ITreeItemContentProvider;
import org.eclipse.equinox.internal.provisional.p2.query.Collector;
import org.eclipse.equinox.internal.provisional.p2.query.Query;

/**
 * This is the item provider adapter for a {@link org.eclipse.buckminster.aggregator.InstallableUnitReference} object.
 * <!-- begin-user-doc --> <!-- end-user-doc -->
 * @generated
 */
public class InstallableUnitReferenceItemProvider extends AggregatorItemProviderAdapter implements
		IEditingDomainItemProvider, IStructuredItemContentProvider, ITreeItemContentProvider, IItemLabelProvider,
		IItemPropertySource
{
	/**
	 * This constructs an instance from a factory and a notifier.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	public InstallableUnitReferenceItemProvider(AdapterFactory adapterFactory)
	{
		super(adapterFactory);
	}

	/**
	 * This returns the property descriptors for the adapted class.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public List<IItemPropertyDescriptor> getPropertyDescriptors(Object object)
	{
		if (itemPropertyDescriptors == null)
		{
			super.getPropertyDescriptors(object);

			addInstallableUnitPropertyDescriptor(object);
		}
		return itemPropertyDescriptors;
	}

	/**
	 * Return the resource locator for this item provider's resources.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public ResourceLocator getResourceLocator()
	{
		return AggregatorEditPlugin.INSTANCE;
	}

	/**
	 * This returns the label text for the adapted class.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getText(Object object)
	{
		return getString("_UI_InstallableUnitReference_type");
	}

	/**
	 * This handles model notifications by calling {@link #updateChildren} to update any cached children and by creating
	 * a viewer notification, which it passes to {@link #fireNotifyChanged}. <!-- begin-user-doc --> <!-- end-user-doc
	 * -->
	 * 
	 * @generated
	 */
	@Override
	public void notifyChanged(Notification notification)
	{
		updateChildren(notification);
		super.notifyChanged(notification);
	}

	/**
	 * This adds a property descriptor for the Installable Unit feature. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated NOT
	 */
	protected void addInstallableUnitPropertyDescriptor(Object object)
	{
		itemPropertyDescriptors.add(new ContributionItemProvider.DynamicItemPropertyDescriptor(
				((ComposeableAdapterFactory)adapterFactory).getRootAdapterFactory(), getResourceLocator(),
				getString("_UI_InstallableUnitReference_installableUnit_feature"), getString(
						"_UI_PropertyDescriptor_description", "_UI_InstallableUnitReference_installableUnit_feature",
						"_UI_InstallableUnitReference_type"),
				AggregatorPackage.Literals.INSTALLABLE_UNIT_REFERENCE__INSTALLABLE_UNIT, true, false, true, null, null,
				null)
		{
			@SuppressWarnings("unchecked")
			public Collection<?> getChoiceOfValues(Object object)
			{
				InstallableUnitReference self = (InstallableUnitReference)object;
				MappedRepository container = (MappedRepository)self.eContainer();
				MetadataRepository repo = container.getMetadataRepository();
				if(repo == null)
					return Collections.singleton(null);

				// Build a list of IU's that correspond to the given type of MappedUnit
				//
				Collector collector = repo.query(getInstallableUnitQuery(), new Collector(), null);
				if(collector.isEmpty())
					return Collections.singleton(null);

				List<InstallableUnit> result = new ArrayList<InstallableUnit>();
				result.add(null);

				Collection availableUnits = collector.toCollection();

				// if current installable unit is not among the newly retrieved ones,
				// add it to the choice values so that user would not be surprised by
				// disappearing current choice after clicking on the property value
				if(self.getInstallableUnit() != null && !availableUnits.contains(self.getInstallableUnit()))
					result.add(self.getInstallableUnit());

				result.addAll(availableUnits);

				// Exclude IU's that are already mapped
				//
				for(InstallableUnitReference iuRef : getContainerChildren(container))
				{
					if(iuRef == self)
						continue;

					InstallableUnit iu = iuRef.getInstallableUnit();
					if(iu == null)
						continue;

					int idx = result.indexOf(iu);
					if(idx >= 0)
						result.remove(idx);
				}
				return result;
			}
		});
	}

	/**
	 * This adds {@link org.eclipse.emf.edit.command.CommandParameter}s describing the children
	 * that can be created under this object.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected void collectNewChildDescriptors(Collection<Object> newChildDescriptors, Object object)
	{
		super.collectNewChildDescriptors(newChildDescriptors, object);
	}

	// Must be implemented by subclass.
	protected List<? extends InstallableUnitReference> getContainerChildren(MappedRepository container)
	{
		throw new UnsupportedOperationException();
	}

	// Must be implemented by subclass.
	protected Query getInstallableUnitQuery()
	{
		throw new UnsupportedOperationException();
	}
}