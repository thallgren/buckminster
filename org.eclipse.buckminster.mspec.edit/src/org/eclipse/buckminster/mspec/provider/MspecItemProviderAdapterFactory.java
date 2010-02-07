/**
 * <copyright>
 * </copyright>
 *
 * $Id$
 */
package org.eclipse.buckminster.mspec.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.buckminster.model.common.CommonFactory;
import org.eclipse.buckminster.model.common.CommonPackage;
import org.eclipse.buckminster.model.common.Documentation;
import org.eclipse.buckminster.model.common.util.CommonSwitch;
import org.eclipse.buckminster.mspec.MspecPackage;

import org.eclipse.buckminster.mspec.util.MspecAdapterFactory;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.Notifier;

import org.eclipse.emf.common.util.ResourceLocator;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.FeatureMapUtil;
import org.eclipse.emf.edit.command.CommandParameter;
import org.eclipse.emf.edit.domain.EditingDomain;

import org.eclipse.emf.edit.provider.ChangeNotifier;
import org.eclipse.emf.edit.provider.ChildCreationExtenderManager;
import org.eclipse.emf.edit.provider.ComposeableAdapterFactory;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.IChangeNotifier;
import org.eclipse.emf.edit.provider.IChildCreationExtender;
import org.eclipse.emf.edit.provider.IDisposable;
import org.eclipse.emf.edit.provider.IEditingDomainItemProvider;
import org.eclipse.emf.edit.provider.IItemLabelProvider;
import org.eclipse.emf.edit.provider.IItemPropertySource;
import org.eclipse.emf.edit.provider.INotifyChangedListener;
import org.eclipse.emf.edit.provider.IStructuredItemContentProvider;
import org.eclipse.emf.edit.provider.ITreeItemContentProvider;

/**
 * This is the factory that is used to provide the interfaces needed to support
 * Viewers. The adapters generated by this factory convert EMF adapter
 * notifications into calls to {@link #fireNotifyChanged fireNotifyChanged}. The
 * adapters also support Eclipse property sheets. Note that most of the adapters
 * are shared among multiple instances. <!-- begin-user-doc --> <!--
 * end-user-doc -->
 * 
 * @generated
 */
public class MspecItemProviderAdapterFactory extends MspecAdapterFactory implements ComposeableAdapterFactory, IChangeNotifier, IDisposable,
		IChildCreationExtender {
	/**
	 * A child creation extender for the {@link CommonPackage}. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public static class CommonChildCreationExtender implements IChildCreationExtender {
		/**
		 * The switch for creating child descriptors specific to each extended
		 * class. <!-- begin-user-doc --> <!-- end-user-doc -->
		 * 
		 * @generated
		 */
		protected static class CreationSwitch extends CommonSwitch<Object> {
			/**
			 * The child descriptors being populated. <!-- begin-user-doc -->
			 * <!-- end-user-doc -->
			 * 
			 * @generated
			 */
			protected List<Object> newChildDescriptors;

			/**
			 * The domain in which to create the children. <!-- begin-user-doc
			 * --> <!-- end-user-doc -->
			 * 
			 * @generated
			 */
			protected EditingDomain editingDomain;

			/**
			 * Creates the a switch for populating child descriptors in the
			 * given domain. <!-- begin-user-doc --> <!-- end-user-doc -->
			 * 
			 * @generated
			 */
			CreationSwitch(List<Object> newChildDescriptors, EditingDomain editingDomain) {
				this.newChildDescriptors = newChildDescriptors;
				this.editingDomain = editingDomain;
			}

			/**
			 * <!-- begin-user-doc --> <!-- end-user-doc -->
			 * 
			 * @generated
			 */
			@Override
			public Object caseDocumentation(Documentation object) {
				newChildDescriptors.add(createChildParameter(CommonPackage.Literals.DOCUMENTATION__MIXED, FeatureMapUtil.createEntry(
						MspecPackage.Literals.DOCUMENT_ROOT__PROPERTY, CommonFactory.eINSTANCE.createPropertyConstant())));

				newChildDescriptors.add(createChildParameter(CommonPackage.Literals.DOCUMENTATION__MIXED, FeatureMapUtil.createEntry(
						MspecPackage.Literals.DOCUMENT_ROOT__PROPERTY_ELEMENT, CommonFactory.eINSTANCE.createPropertyElement())));

				return null;
			}

			/**
			 * <!-- begin-user-doc --> <!-- end-user-doc -->
			 * 
			 * @generated
			 */
			protected CommandParameter createChildParameter(Object feature, Object child) {
				return new CommandParameter(null, feature, child);
			}

		}

		/**
		 * <!-- begin-user-doc --> <!-- end-user-doc -->
		 * 
		 * @generated
		 */
		public Collection<Object> getNewChildDescriptors(Object object, EditingDomain editingDomain) {
			ArrayList<Object> result = new ArrayList<Object>();
			new CreationSwitch(result, editingDomain).doSwitch((EObject) object);
			return result;
		}

		/**
		 * <!-- begin-user-doc --> <!-- end-user-doc -->
		 * 
		 * @generated
		 */
		public ResourceLocator getResourceLocator() {
			return MspecEditPlugin.INSTANCE;
		}
	}

	/**
	 * This keeps track of the root adapter factory that delegates to this
	 * adapter factory. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected ComposedAdapterFactory parentAdapterFactory;

	/**
	 * This is used to implement
	 * {@link org.eclipse.emf.edit.provider.IChangeNotifier}. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected IChangeNotifier changeNotifier = new ChangeNotifier();

	/**
	 * This helps manage the child creation extenders. <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected ChildCreationExtenderManager childCreationExtenderManager = new ChildCreationExtenderManager(MspecEditPlugin.INSTANCE,
			MspecPackage.eNS_URI);

	/**
	 * This keeps track of all the supported types checked by
	 * {@link #isFactoryForType isFactoryForType}. <!-- begin-user-doc --> <!--
	 * end-user-doc -->
	 * 
	 * @generated
	 */
	protected Collection<Object> supportedTypes = new ArrayList<Object>();

	/**
	 * This keeps track of the one adapter used for all
	 * {@link org.eclipse.emf.ecore.EObject} instances. <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected DocumentRootItemProvider documentRootItemProvider;

	/**
	 * This keeps track of the one adapter used for all
	 * {@link org.eclipse.buckminster.mspec.MaterializationNode} instances. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected MaterializationNodeItemProvider materializationNodeItemProvider;

	/**
	 * This keeps track of the one adapter used for all
	 * {@link org.eclipse.buckminster.mspec.MaterializationDirective} instances.
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected MaterializationDirectiveItemProvider materializationDirectiveItemProvider;

	/**
	 * This keeps track of the one adapter used for all
	 * {@link org.eclipse.buckminster.mspec.MaterializationSpec} instances. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected MaterializationSpecItemProvider materializationSpecItemProvider;

	/**
	 * This keeps track of the one adapter used for all
	 * {@link org.eclipse.buckminster.mspec.Unpack} instances. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	protected UnpackItemProvider unpackItemProvider;

	/**
	 * This constructs an instance. <!-- begin-user-doc --> <!-- end-user-doc
	 * -->
	 * 
	 * @generated
	 */
	public MspecItemProviderAdapterFactory() {
		supportedTypes.add(IEditingDomainItemProvider.class);
		supportedTypes.add(IStructuredItemContentProvider.class);
		supportedTypes.add(ITreeItemContentProvider.class);
		supportedTypes.add(IItemLabelProvider.class);
		supportedTypes.add(IItemPropertySource.class);
	}

	/**
	 * This implementation substitutes the factory itself as the key for the
	 * adapter. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public Adapter adapt(Notifier notifier, Object type) {
		return super.adapt(notifier, this);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public Object adapt(Object object, Object type) {
		if (isFactoryForType(type)) {
			Object adapter = super.adapt(object, type);
			if (!(type instanceof Class<?>) || (((Class<?>) type).isInstance(adapter))) {
				return adapter;
			}
		}

		return null;
	}

	/**
	 * This adds a listener. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public void addListener(INotifyChangedListener notifyChangedListener) {
		changeNotifier.addListener(notifyChangedListener);
	}

	/**
	 * This creates an adapter for a {@link org.eclipse.emf.ecore.EObject}. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public Adapter createDocumentRootAdapter() {
		if (documentRootItemProvider == null) {
			documentRootItemProvider = new DocumentRootItemProvider(this);
		}

		return documentRootItemProvider;
	}

	/**
	 * This creates an adapter for a
	 * {@link org.eclipse.buckminster.mspec.MaterializationDirective}. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public Adapter createMaterializationDirectiveAdapter() {
		if (materializationDirectiveItemProvider == null) {
			materializationDirectiveItemProvider = new MaterializationDirectiveItemProvider(this);
		}

		return materializationDirectiveItemProvider;
	}

	/**
	 * This creates an adapter for a
	 * {@link org.eclipse.buckminster.mspec.MaterializationNode}. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public Adapter createMaterializationNodeAdapter() {
		if (materializationNodeItemProvider == null) {
			materializationNodeItemProvider = new MaterializationNodeItemProvider(this);
		}

		return materializationNodeItemProvider;
	}

	/**
	 * This creates an adapter for a
	 * {@link org.eclipse.buckminster.mspec.MaterializationSpec}. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public Adapter createMaterializationSpecAdapter() {
		if (materializationSpecItemProvider == null) {
			materializationSpecItemProvider = new MaterializationSpecItemProvider(this);
		}

		return materializationSpecItemProvider;
	}

	/**
	 * This creates an adapter for a
	 * {@link org.eclipse.buckminster.mspec.Unpack}. <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public Adapter createUnpackAdapter() {
		if (unpackItemProvider == null) {
			unpackItemProvider = new UnpackItemProvider(this);
		}

		return unpackItemProvider;
	}

	/**
	 * This disposes all of the item providers created by this factory. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public void dispose() {
		if (documentRootItemProvider != null)
			documentRootItemProvider.dispose();
		if (materializationNodeItemProvider != null)
			materializationNodeItemProvider.dispose();
		if (materializationDirectiveItemProvider != null)
			materializationDirectiveItemProvider.dispose();
		if (materializationSpecItemProvider != null)
			materializationSpecItemProvider.dispose();
		if (unpackItemProvider != null)
			unpackItemProvider.dispose();
	}

	/**
	 * This delegates to {@link #changeNotifier} and to
	 * {@link #parentAdapterFactory}. <!-- begin-user-doc --> <!-- end-user-doc
	 * -->
	 * 
	 * @generated
	 */
	public void fireNotifyChanged(Notification notification) {
		changeNotifier.fireNotifyChanged(notification);

		if (parentAdapterFactory != null) {
			parentAdapterFactory.fireNotifyChanged(notification);
		}
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public List<IChildCreationExtender> getChildCreationExtenders() {
		return childCreationExtenderManager.getChildCreationExtenders();
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public Collection<?> getNewChildDescriptors(Object object, EditingDomain editingDomain) {
		return childCreationExtenderManager.getNewChildDescriptors(object, editingDomain);
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public ResourceLocator getResourceLocator() {
		return childCreationExtenderManager;
	}

	/**
	 * This returns the root adapter factory that contains this factory. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public ComposeableAdapterFactory getRootAdapterFactory() {
		return parentAdapterFactory == null ? this : parentAdapterFactory.getRootAdapterFactory();
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	@Override
	public boolean isFactoryForType(Object type) {
		return supportedTypes.contains(type) || super.isFactoryForType(type);
	}

	/**
	 * This removes a listener. <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public void removeListener(INotifyChangedListener notifyChangedListener) {
		changeNotifier.removeListener(notifyChangedListener);
	}

	/**
	 * This sets the composed adapter factory that contains this factory. <!--
	 * begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 */
	public void setParentAdapterFactory(ComposedAdapterFactory parentAdapterFactory) {
		this.parentAdapterFactory = parentAdapterFactory;
	}

}
