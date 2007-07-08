/*******************************************************************
 * Copyright (c) 2006-2007, Cloudsmith Inc.
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of Cloudsmith Inc. and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from Cloudsmith Inc.
 ******************************************************************/

package org.eclipse.buckminster.ui.editor.query;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.buckminster.core.CorePlugin;
import org.eclipse.buckminster.core.common.model.Documentation;
import org.eclipse.buckminster.core.cspec.model.ComponentRequest;
import org.eclipse.buckminster.core.ctype.AbstractComponentType;
import org.eclipse.buckminster.core.helpers.TextUtils;
import org.eclipse.buckminster.core.parser.IParser;
import org.eclipse.buckminster.core.query.builder.AdvisorNodeBuilder;
import org.eclipse.buckminster.core.query.builder.ComponentQueryBuilder;
import org.eclipse.buckminster.core.query.model.ComponentQuery;
import org.eclipse.buckminster.core.query.model.MutableLevel;
import org.eclipse.buckminster.core.query.model.SourceLevel;
import org.eclipse.buckminster.core.version.IVersionDesignator;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.IOUtils;
import org.eclipse.buckminster.runtime.Trivial;
import org.eclipse.buckminster.runtime.URLUtils;
import org.eclipse.buckminster.ui.DynamicTableLayout;
import org.eclipse.buckminster.ui.ExternalFileEditorInput;
import org.eclipse.buckminster.ui.UiUtils;
import org.eclipse.buckminster.ui.actions.BlankQueryAction;
import org.eclipse.buckminster.ui.editor.EditorUtils;
import org.eclipse.buckminster.ui.editor.Properties;
import org.eclipse.buckminster.ui.editor.PropertiesModifyEvent;
import org.eclipse.buckminster.ui.editor.PropertiesModifyListener;
import org.eclipse.buckminster.ui.editor.SaveRunnable;
import org.eclipse.buckminster.ui.editor.VersionDesignator;
import org.eclipse.buckminster.ui.editor.VersionDesignatorEvent;
import org.eclipse.buckminster.ui.editor.VersionDesignatorListener;
import org.eclipse.buckminster.ui.internal.ResolveJob;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.SaveAsDialog;
import org.eclipse.ui.editors.text.ILocationProvider;
import org.eclipse.ui.part.EditorPart;
import org.xml.sax.SAXException;

/**
 * @author Karel Brezina
 * 
 */
public class QueryEditor extends EditorPart
{
	class AdvisorNodeLabelProvider extends LabelProvider implements ITableLabelProvider
	{
		public Image getColumnImage(Object element, int columnIndex)
		{
			return null;
		}

		public String getColumnText(Object element, int columnIndex)
		{
			AdvisorNodeBuilder node = (AdvisorNodeBuilder)element;
			String lbl;
			switch(columnIndex)
			{
			case 0:
				lbl = node.getNamePattern().toString(); 
				break;
			case 1:
				lbl = node.getComponentTypeID();
				break;
			default:
				lbl = null;
			}
			return lbl;
		}
	}

	class CompoundModifyListener implements VersionDesignatorListener, ModifyListener, PropertiesModifyListener
	{

		public void modifyProperties(PropertiesModifyEvent e)
		{
			setDirty(true);
		}

		public void modifyText(ModifyEvent e)
		{
			setDirty(true);
		}

		public void modifyVersionDesignator(VersionDesignatorEvent e)
		{
			setDirty(true);
		}
	}

	class CheckboxSelectionListener extends SelectionAdapter
	{
		private Control[] m_controlsToEnable;

		public CheckboxSelectionListener(Control[] controlsToEnable)
		{
			m_controlsToEnable = controlsToEnable;
		}

		@Override
		public void widgetSelected(SelectionEvent e)
		{
			Button button = (Button)e.widget;
			boolean enable = button.getSelection();

			for(Control control : m_controlsToEnable)
			{
				control.setEnabled(enable);
			}
		}
	}

	private static final String TEMP_FILE_PREFIX = "bmqtmp-";
	
	private CTabFolder m_tabFolder;

	private Text m_componentName;

	private Combo m_componentType;

	private VersionDesignator m_versionDesignator;

	private ComponentQueryBuilder m_componentQuery;

	private Button m_editOrCancelButton;

	private Button m_enableOverride;

	private boolean m_nodeEditMode;

	private boolean m_hasChanges;

	private Button m_resolveButton;

	private Button m_materializeButton;

	private Button m_externalSaveAsButton;

	private Button m_moveDownButton;

	private Button m_moveUpButton;

	private boolean m_mute;

	private Combo m_mutableLevel;

	private Text m_namePattern;

	private Combo m_category;

	private Text m_overlayFolder;

	private Button m_overlayBrowseButton;

	private Text m_wantedAttributes;

	private Button m_prune;

	private boolean m_needsRefresh;

	private Button m_newOrSaveButton;

	private TableViewer m_nodeTable;

	private Button m_removeButton;

	private Button m_requestURLCheckbox;

	private Text m_requestURL;

	private Button m_propertyURLCheckbox;

	private Text m_propertyURL;

	private Tree m_nodeTree;

	private Combo m_sourceLevel;

	private Button m_skipComponent;

	private Button m_allowCircular;

	private Composite m_nodesStackComposite;

	private StackLayout m_nodesStackLayout;

	private HashMap<String, Control> m_nodesHash;

	private Button m_useInstalled;

	private Button m_useMaterialization;

	private Button m_useResolutionService;

	private VersionDesignator m_versionOverride;

	private boolean m_continueOnError;

	private Properties m_nodeProperties;

	private Text m_nodeDocumentation;

	private Properties m_properties;

	private Text m_shortDesc;

	private Text m_documentation;

	private CompoundModifyListener m_compoundModifyListener;

	public String commitChanges(ComponentRequest[] requestRet)
	{
		String name = UiUtils.trimmedValue(m_componentName);
		if(name == null)
			return "The component must have a name";

		String category = null;
		int idx = m_componentType.getSelectionIndex();
		if(idx >= 0)
		{
			category = m_componentType.getItem(idx);
			if(category.length() == 0)
				category = null;
		}
		requestRet[0] = new ComponentRequest(name, category, m_versionDesignator.getVersionDesignator());
		return null;
	}

	@Override
	public void createPartControl(Composite parent)
	{
		Composite topComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, true);
		layout.marginHeight = layout.marginWidth = 0;
		topComposite.setLayout(layout);

		m_tabFolder = new CTabFolder(topComposite, SWT.BOTTOM);
		m_tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		CTabItem mainTab = new CTabItem(m_tabFolder, SWT.NONE);
		mainTab.setText("Main");
		mainTab.setControl(getMainTabControl(m_tabFolder));

		CTabItem advisorTab = new CTabItem(m_tabFolder, SWT.NONE);
		advisorTab.setText("Advisor Nodes");
		advisorTab.setControl(getAdvisorTabControl(m_tabFolder));

		CTabItem propertiesTab = new CTabItem(m_tabFolder, SWT.NONE);
		propertiesTab.setText("Properties");
		propertiesTab.setControl(getPropertiesTabControl(m_tabFolder));

		CTabItem documentationTab = new CTabItem(m_tabFolder, SWT.NONE);
		documentationTab.setText("Documentation");
		documentationTab.setControl(getDocumentationTabControl(m_tabFolder));

		createActionButtons(topComposite);

	}

	public void doExternalSaveAs()
	{
		if(!commitChangesToQuery())
			return;
		FileDialog dlg = new FileDialog(getSite().getShell(), SWT.SAVE);
		dlg.setFilterExtensions(new String[] { "*.cquery" });
		final String location = dlg.open();
		if(location == null)
			return;
		saveToPath(new Path(location));
	}

	@Override
	public void doSave(IProgressMonitor monitor)
	{
		if(!commitChangesToQuery())
			return;

		IEditorInput input = getEditorInput();
		if(input == null)
			return;

		IPath path = (input instanceof ILocationProvider)
				? ((ILocationProvider)input).getPath(input)
				: ((IPathEditorInput)input).getPath();

		saveToPath(path);
	}

	@Override
	public void doSaveAs()
	{
		if(!commitChangesToQuery())
			return;

		IEditorInput input = getEditorInput();
		if(input == null)
			return;

		SaveAsDialog dialog = new SaveAsDialog(getSite().getShell());
		IFile original = (input instanceof IFileEditorInput)
				? ((IFileEditorInput)input).getFile()
				: null;
		if(original != null)
			dialog.setOriginalFile(original);

		if(dialog.open() == Window.CANCEL)
			return;

		IPath filePath = dialog.getResult();
		if(filePath == null)
			return;

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IFile file = workspace.getRoot().getFile(filePath);
		saveToPath(file.getLocation());
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException
	{
		if(!(input instanceof ILocationProvider || input instanceof IPathEditorInput || input instanceof IURIEditorInput))
			throw new PartInitException("Invalid Input");

		setSite(site);

		if(input instanceof IURIEditorInput)
		{
			try
			{
				URI uri = ((IURIEditorInput) input).getURI();
				URL url = uri.toURL();
				String protocol = url.getProtocol();
				
				File queryFile = null;
				
				if(protocol == null || "file".equals(protocol))
				{
					queryFile = new File(uri);
				}
				
				if(queryFile == null || !queryFile.canWrite())
				{
					queryFile = File.createTempFile(TEMP_FILE_PREFIX, ".cquery");
					queryFile.deleteOnExit();
					InputStream is = null;
					OutputStream os = null;
					try
					{
						is = URLUtils.openStream(url, null);
						os = new FileOutputStream(queryFile);
						IOUtils.copy(is, os);
					}
					finally
					{
						IOUtils.close(is);
						IOUtils.close(os);
					}
				}
				
				input = new ExternalFileEditorInput(queryFile, new Path(uri.getPath()).lastSegment(), uri.toString());
			}
			catch(Exception e)
			{
				UiUtils.openError(null, "Unable to open editor", e);
			}
		}
		
		InputStream stream = null;
		try
		{
			IPath path = (input instanceof ILocationProvider)
					? ((ILocationProvider)input).getPath(input)
					: ((IPathEditorInput)input).getPath();

			File file = path.toFile();
			m_componentQuery = new ComponentQueryBuilder();
			if(file.length() == 0)
			{
				String defaultName = file.getName();
				if(defaultName.startsWith(BlankQueryAction.TEMP_FILE_PREFIX))
					defaultName = "";
				else
				{
					int lastDot = defaultName.lastIndexOf('.');
					if(lastDot > 0)
						defaultName = defaultName.substring(0, lastDot);
				}
				m_componentQuery.setRootRequest(new ComponentRequest(defaultName, null, null));
			}
			else
			{
				String systemId = file.toString();
				stream = new FileInputStream(file);
				IParser<ComponentQuery> parser = CorePlugin.getDefault().getParserFactory().getComponentQueryParser(
						true);
				m_componentQuery.initFrom(parser.parse(systemId, stream));
			}
			m_needsRefresh = true;
			if(m_componentName != null)
			{
				refreshQuery();
			}
			setInputWithNotify(input);
			setPartName(input.getName());
		}
		catch(SAXException e)
		{
			throw new PartInitException(BuckminsterException.wrap(e).getMessage());
		}
		catch(FileNotFoundException e)
		{
			throw new PartInitException(e.getMessage());
		}
		finally
		{
			IOUtils.close(stream);
		}

		m_compoundModifyListener = new CompoundModifyListener();
	}

	@Override
	public boolean isDirty()
	{
		return m_hasChanges;
	}

	@Override
	public boolean isSaveAsAllowed()
	{
		return true;
	}

	@Override
	public void setFocus()
	{
		m_tabFolder.setFocus();

		if(m_needsRefresh)
			refreshQuery();
	}

	private void cancelNode()
	{
		m_nodeEditMode = false;
		enableDisableButtonGroup();
		refreshNodeFields();
	}

	private boolean commitChangesToQuery()
	{
		if(m_nodeEditMode)
		{
			if(!MessageDialog.openConfirm(getSite().getShell(), null, "Do you want to discard the current node edit?"))
				return false;
			cancelNode();
		}

		try
		{
			String tmp = UiUtils.trimmedValue(m_requestURL);
			m_componentQuery.setResourceMapURL(URLUtils.normalizeToURL(tmp));

			tmp = UiUtils.trimmedValue(m_propertyURL);
			m_componentQuery.setPropertiesURL(URLUtils.normalizeToURL(tmp));
		}
		catch(MalformedURLException e)
		{
			MessageDialog.openError(getSite().getShell(), null, e.getMessage());
			return false;
		}

		m_properties.fillProperties(m_componentQuery.getProperties());
		
		String doc = UiUtils.trimmedValue(m_shortDesc);
		m_componentQuery.setShortDesc(doc);

		doc = UiUtils.trimmedValue(m_documentation);
		try
		{
			m_componentQuery.setDocumentation(doc == null ? null : Documentation.parse(doc));
		}
		catch(CoreException e)
		{
			MessageDialog.openError(getSite().getShell(), null, e.getMessage());
			return false;
		}

		ComponentRequest[] requestRet = new ComponentRequest[1];
		String error = commitChanges(requestRet);
		if(error == null)
			m_componentQuery.setRootRequest(requestRet[0]);
		else
		{
			MessageDialog.openError(getSite().getShell(), null, error);
			return false;
		}
		return true;
	}

	private void createActionButtons(Composite parent)
	{
		Composite allButtonsBox = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		// layout.marginHeight = layout.marginWidth = 0;
		allButtonsBox.setLayout(layout);
		allButtonsBox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		UiUtils.createCheckButton(allButtonsBox, "Continue on error", new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				m_continueOnError = ((Button)e.getSource()).getSelection();
			}
		});

		Composite pressButtonsBox = new Composite(allButtonsBox, SWT.NONE);
		layout = new GridLayout(3, true);
		layout.marginHeight = layout.marginWidth = 0;
		pressButtonsBox.setLayout(layout);
		pressButtonsBox.setLayoutData(new GridData(SWT.END, SWT.FILL, true, false));

		m_resolveButton = UiUtils.createPushButton(pressButtonsBox, "Resolve to Wizard", new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				loadComponent(false);
			}
		});
		m_resolveButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));

		m_materializeButton = UiUtils.createPushButton(pressButtonsBox, "Resolve and Materialize",
				new SelectionAdapter()
				{
					@Override
					public void widgetSelected(SelectionEvent e)
					{
						loadComponent(true);
					}
				});
		m_materializeButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));

		m_externalSaveAsButton = UiUtils.createPushButton(pressButtonsBox, "External Save As", new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				doExternalSaveAs();
			}
		});
		m_externalSaveAsButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
	}

	private void createButtonBox(Composite parent)
	{
		Composite buttonBox = new Composite(parent, SWT.NULL);
		buttonBox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
		FillLayout layout = new FillLayout(SWT.VERTICAL);
		layout.marginWidth = layout.marginHeight = 0;
		layout.spacing = 3;
		buttonBox.setLayout(layout);

		Composite buttonBox1 = new Composite(buttonBox, SWT.NULL);
		// buttonBox1.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false,
		// false));
		layout = new FillLayout(SWT.HORIZONTAL);
		layout.marginWidth = layout.marginHeight = 0;
		buttonBox1.setLayout(layout);

		Composite buttonBox2 = new Composite(buttonBox, SWT.NULL);
		// buttonBox2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false,
		// false));
		layout = new FillLayout(SWT.HORIZONTAL);
		layout.marginWidth = layout.marginHeight = 0;
		buttonBox2.setLayout(layout);

		m_newOrSaveButton = UiUtils.createPushButton(buttonBox1, "New", new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				newOrSaveNode();
			}
		});

		m_editOrCancelButton = UiUtils.createPushButton(buttonBox1, "Edit", new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				editOrCancelNode();
			}
		});

		m_removeButton = UiUtils.createPushButton(buttonBox1, "Remove", new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				removeNode();
			}
		});

		m_moveUpButton = UiUtils.createPushButton(buttonBox2, "Move up", new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				swapAndReselect(0, -1);
			}
		});

		m_moveDownButton = UiUtils.createPushButton(buttonBox2, "Move down", new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				swapAndReselect(1, 0);
			}
		});
	}

	private void createNodeFields(Composite parent)
	{
		createNodeTree(parent);

		createNodeStack(parent);
	}

	private void createNodeStack(Composite parent)
	{
		m_nodesStackComposite = new Composite(parent, SWT.NONE);
		m_nodesStackComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		m_nodesStackLayout = new StackLayout();
		m_nodesStackLayout.marginHeight = m_nodesStackLayout.marginWidth = 0;
		m_nodesStackComposite.setLayout(m_nodesStackLayout);

		m_nodesHash = new HashMap<String, Control>();

		Composite geComposite = new Composite(m_nodesStackComposite, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginHeight = layout.marginWidth = 0;
		geComposite.setLayout(layout);

		m_nodesHash.put("General", geComposite);

		EditorUtils.createHeaderLabel(geComposite, "General", 2);

		UiUtils.createGridLabel(geComposite, "Name pattern:", 1, 0, SWT.NONE);

		m_namePattern = UiUtils.createGridText(geComposite, 1, 0, SWT.NONE);

		UiUtils.createGridLabel(geComposite, "Matched Component Type:", 1, 0, SWT.NONE);

		m_category = UiUtils.createGridCombo(geComposite, 1, 0, null, null, SWT.DROP_DOWN | SWT.READ_ONLY
				| SWT.SIMPLE);
		m_category.setItems(AbstractComponentType.getComponentTypeIDs(true));

		UiUtils.createGridLabel(geComposite, "Skip Component:", 1, 0, SWT.NONE);
		m_skipComponent = UiUtils.createCheckButton(geComposite, null, new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				enableDisableSkipSensitive();
			}
		});
		m_skipComponent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		UiUtils.createGridLabel(geComposite, "Allow Circular Dependency:", 1, 0, SWT.NONE);
		m_allowCircular = UiUtils.createCheckButton(geComposite, null, null);

		Composite aqComposite = new Composite(m_nodesStackComposite, SWT.NONE);
		layout = new GridLayout(2, false);
		layout.marginHeight = layout.marginWidth = 0;
		aqComposite.setLayout(layout);

		m_nodesHash.put("Attribute Qualification", aqComposite);

		EditorUtils.createHeaderLabel(aqComposite, "Attribute Qualification", 2);

		UiUtils.createGridLabel(aqComposite, "Attributes:", 1, 0, SWT.NONE);
		m_wantedAttributes = UiUtils.createGridText(aqComposite, 0, 0, SWT.NONE);
		UiUtils.createGridLabel(aqComposite, "Prune According To Attributes:", 1, 0, SWT.NONE);
		m_prune = UiUtils.createCheckButton(aqComposite, null, null);

		Composite srComposite = new Composite(m_nodesStackComposite, SWT.NONE);
		layout = new GridLayout(2, false);
		layout.marginHeight = layout.marginWidth = 0;
		srComposite.setLayout(layout);

		m_nodesHash.put("Special Requirements", srComposite);

		EditorUtils.createHeaderLabel(srComposite, "Special Requirements", 2);

		UiUtils.createGridLabel(srComposite, "Mutable level:", 1, 0, SWT.NONE);
		m_mutableLevel = UiUtils.createGridEnumCombo(srComposite, 0, 0, MutableLevel.values(), null, null, SWT.NONE);
		UiUtils.createGridLabel(srComposite, "Source level:", 1, 0, SWT.NONE);
		m_sourceLevel = UiUtils.createGridEnumCombo(srComposite, 0, 0, SourceLevel.values(), null, null, SWT.NONE);

		Composite kuComposite = new Composite(m_nodesStackComposite, SWT.NONE);
		layout = new GridLayout(2, false);
		layout.marginHeight = layout.marginWidth = 0;
		kuComposite.setLayout(layout);

		m_nodesHash.put("Resolution Scope", kuComposite);

		EditorUtils.createHeaderLabel(kuComposite, "Resolution Scope", 2);

		UiUtils.createGridLabel(kuComposite, "Target Platform:", 1, 0, SWT.NONE);
		m_useInstalled = UiUtils.createCheckButton(kuComposite, null, null);
		UiUtils.createGridLabel(kuComposite, "Materialization:", 1, 0, SWT.NONE);
		m_useMaterialization = UiUtils.createCheckButton(kuComposite, null, null);
		UiUtils.createGridLabel(kuComposite, "Resolution Service:", 1, 0, SWT.NONE);
		m_useResolutionService = UiUtils.createCheckButton(kuComposite, null, null);

		Composite ovComposite = new Composite(m_nodesStackComposite, SWT.NONE);
		layout = new GridLayout(3, false);
		layout.marginHeight = layout.marginWidth = 0;
		ovComposite.setLayout(layout);

		m_nodesHash.put("Override", ovComposite);

		EditorUtils.createHeaderLabel(ovComposite, "Override", 3);

		UiUtils.createGridLabel(ovComposite, "Override version", 1, 0, SWT.NONE);
		m_enableOverride = UiUtils.createCheckButton(ovComposite, null, new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				boolean selected = ((Button)e.getSource()).getSelection();
				m_versionOverride.setEnabled(selected);
			}
		});
		UiUtils.createEmptyLabel(ovComposite);

		m_versionOverride = new VersionDesignator(ovComposite);
		m_nodeEditMode = false;

		Composite ofComposite = new Composite(m_nodesStackComposite, SWT.NONE);
		layout = new GridLayout(2, false);
		layout.marginHeight = layout.marginWidth = 0;
		ofComposite.setLayout(layout);

		m_nodesHash.put("Overlay Folder", ofComposite);

		EditorUtils.createHeaderLabel(ofComposite, "Overlay folder (for prototyping)", 2);

		UiUtils.createGridLabel(ofComposite, "Folder:", 1, 0, SWT.NONE);
		m_overlayFolder = UiUtils.createGridText(ofComposite, 1, 0, SWT.NONE);
		Label label = UiUtils.createEmptyLabel(ofComposite);
		label.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
		m_overlayBrowseButton = new Button(ofComposite, SWT.PUSH);
		m_overlayBrowseButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		m_overlayBrowseButton.setText("Browse...");
		m_overlayBrowseButton.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent se)
			{
				DirectoryDialog dlg = new DirectoryDialog(getSite().getShell());
				m_overlayFolder.setText(TextUtils.notNullString(dlg.open()));
			}
		});

		Composite prComposite = new Composite(m_nodesStackComposite, SWT.NONE);
		layout = new GridLayout(1, false);
		layout.marginHeight = layout.marginWidth = 0;
		prComposite.setLayout(layout);

		m_nodesHash.put("Properties", prComposite);

		EditorUtils.createHeaderLabel(prComposite, "Properties", 1);

		m_nodeProperties = new Properties(prComposite, SWT.NONE);
		m_nodeProperties.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite docComposite = new Composite(m_nodesStackComposite, SWT.NONE);
		layout = new GridLayout(1, false);
		layout.marginHeight = layout.marginWidth = 0;
		docComposite.setLayout(layout);

		m_nodesHash.put("Documentation", docComposite);

		EditorUtils.createHeaderLabel(docComposite, "Documentation", 1);

		m_nodeDocumentation = UiUtils.createGridText(docComposite, 1, 0, SWT.MULTI);
		m_nodeDocumentation.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		m_nodeTree.setSelection(m_nodeTree.getItem(0));
		m_nodesStackLayout.topControl = geComposite;
		m_nodesStackComposite.layout();
		/*
		 * // set the same height for nodeTable and node Tree int height = m_nodeTree.computeSize(SWT.DEFAULT,
		 * SWT.DEFAULT).y + 35;
		 * 
		 * Table table = (Table) m_nodeTable.getControl(); GridData gridData = (GridData) table.getLayoutData();
		 * gridData.heightHint = height; table.setLayoutData(gridData);
		 * 
		 * gridData = (GridData) m_nodeTree.getLayoutData(); gridData.heightHint = height;
		 * m_nodeTree.setLayoutData(gridData);
		 * 
		 * gridData = (GridData) m_nodesStackComposite.getLayoutData(); gridData.heightHint = height + 21;
		 * m_nodesStackComposite.setLayoutData(gridData);
		 */
	}

	private void createNodeTableGroup(Composite parent)
	{
		Composite componentTableGroup = new Composite(parent, SWT.NONE);
		GridLayout gl = new GridLayout(1, true);
		gl.marginHeight = gl.marginWidth = 0;
		componentTableGroup.setLayout(gl);
		componentTableGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Table table = new Table(componentTableGroup, SWT.BORDER | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL
				| SWT.FULL_SELECTION);

		table.setHeaderVisible(false);

		String[] columnNames = new String[] { "Name Pattern", "Category" };
		int[] columnWeights = new int[] { 10, 5 };

		table.setHeaderVisible(true);
		DynamicTableLayout layout = new DynamicTableLayout(50);
		for(int idx = 0; idx < columnNames.length; idx++)
		{
			TableColumn tableColumn = new TableColumn(table, SWT.LEFT, idx);
			tableColumn.setText(columnNames[idx]);
			layout.addColumnData(new ColumnWeightData(columnWeights[idx], true));
		}
		table.setLayout(layout);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		m_nodeTable = new TableViewer(table);
		m_nodeTable.setLabelProvider(new AdvisorNodeLabelProvider());
		m_nodeTable.setContentProvider(new ArrayContentProvider());
		m_nodeTable.addSelectionChangedListener(new ISelectionChangedListener()
		{
			public void selectionChanged(SelectionChangedEvent event)
			{
				nodeSelectionEvent();
			}
		});
		m_nodeTable.addDoubleClickListener(new IDoubleClickListener()
		{
			public void doubleClick(DoubleClickEvent event)
			{
				if(m_nodeTable.getTable().getSelectionIndex() >= 0)
				{
					editNode();
				}
			}
		});

		createButtonBox(componentTableGroup);
	}

	private void createNodeTree(Composite parent)
	{
		Composite treeComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		treeComposite.setLayout(layout);
		treeComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));

		m_nodeTree = new Tree(treeComposite, SWT.BORDER);

		int width = m_nodeTree.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, false, true);
		gridData.widthHint = width + 40; // m_nodeTree.setSelection made it
		// too small
		m_nodeTree.setLayoutData(gridData);
		m_nodeTree.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				if(e.item != null)
				{
					TreeItem item = (TreeItem)e.item;
					m_nodesStackLayout.topControl = m_nodesHash.get(item.getText());
					m_nodesStackComposite.layout();
				}
			}
		});

		TreeItem item = new TreeItem(m_nodeTree, SWT.NONE);
		item.setText("General");

		item = new TreeItem(m_nodeTree, SWT.NONE);
		item.setText("Attribute Qualification");

		item = new TreeItem(m_nodeTree, SWT.NONE);
		item.setText("Project Name Mapping");

		item = new TreeItem(m_nodeTree, SWT.NONE);
		item.setText("Special Requirements");

		item = new TreeItem(m_nodeTree, SWT.NONE);
		item.setText("Resolution Scope");

		item = new TreeItem(m_nodeTree, SWT.NONE);
		item.setText("Override");

		item = new TreeItem(m_nodeTree, SWT.NONE);
		item.setText("Overlay Folder");

		item = new TreeItem(m_nodeTree, SWT.NONE);
		item.setText("Properties");

		item = new TreeItem(m_nodeTree, SWT.NONE);
		item.setText("Documentation");
	}

	private void editNode()
	{
		m_nodeEditMode = true;
		enableDisableButtonGroup();
		setDirty(true);
	}

	private void editOrCancelNode()
	{
		if(m_nodeEditMode)
			cancelNode();
		else
			editNode();
	}

	private void enableDisableButtonGroup()
	{
		if(m_nodeEditMode)
		{
			// A node is being edited
			//
			m_newOrSaveButton.setText("Save");
			m_editOrCancelButton.setText("Cancel");
			m_editOrCancelButton.setEnabled(true);
			m_removeButton.setEnabled(false);
			m_moveUpButton.setEnabled(false);
			m_moveDownButton.setEnabled(false);
		}
		else
		{
			Table table = m_nodeTable.getTable();
			int top = table.getItemCount();
			int idx = table.getSelectionIndex();
			m_newOrSaveButton.setText("New");
			m_editOrCancelButton.setText("Edit");
			m_editOrCancelButton.setEnabled(idx >= 0);
			m_removeButton.setEnabled(idx >= 0);
			m_moveUpButton.setEnabled(idx > 0);
			m_moveDownButton.setEnabled(idx >= 0 && idx < top - 1);
		}
		m_nodeTable.getTable().setEnabled(!m_nodeEditMode);

		m_namePattern.setEnabled(m_nodeEditMode);
		m_category.setEnabled(m_nodeEditMode);
		m_skipComponent.setEnabled(m_nodeEditMode);
		enableDisableSkipSensitive();
	}

	private void enableDisableSkipSensitive()
	{
		boolean enableRest = m_nodeEditMode && !m_skipComponent.getSelection();

		m_allowCircular.setEnabled(enableRest);
		m_overlayFolder.setEnabled(enableRest);
		m_overlayBrowseButton.setEnabled(enableRest);
		m_wantedAttributes.setEnabled(enableRest);
		m_prune.setEnabled(enableRest);

		m_mutableLevel.setEnabled(enableRest);
		m_sourceLevel.setEnabled(enableRest);

		m_useInstalled.setEnabled(enableRest);
		m_useMaterialization.setEnabled(enableRest);
		m_useResolutionService.setEnabled(enableRest);

		m_enableOverride.setEnabled(enableRest);
		m_versionOverride.setEnabled(enableRest && m_enableOverride.getSelection());

		m_nodeProperties.setEnabled(enableRest);
		m_nodeDocumentation.setEnabled(enableRest);
	}

	private Control getAdvisorTabControl(Composite parent)
	{
		Composite tabComposite = EditorUtils.getNamedTabComposite(parent, "Advisor Nodes");

		Composite advisorComposite = new Composite(tabComposite, SWT.NONE);
		GridLayout layout = new GridLayout(3, false);
		layout.marginHeight = layout.marginWidth = 0;
		advisorComposite.setLayout(layout);
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		advisorComposite.setLayoutData(gridData);

		createNodeTableGroup(advisorComposite);

		createNodeFields(advisorComposite);

		return tabComposite;
	}

	private Control getDocumentationTabControl(Composite parent)
	{
		Composite tabComposite = EditorUtils.getNamedTabComposite(parent, "Documentation");

		Composite descComposite = new Composite(tabComposite, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginHeight = layout.marginWidth = 0;
		descComposite.setLayout(layout);
		descComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		UiUtils.createGridLabel(descComposite, "Short Description:", 1, 0, SWT.NONE);
		m_shortDesc = UiUtils.createGridText(descComposite, 1, 0, SWT.NONE, m_compoundModifyListener);

		Label label = UiUtils.createGridLabel(descComposite, "Documentation:", 1, 0, SWT.NONE);
		label.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, false, false));
		m_documentation = UiUtils.createGridText(descComposite, 1, 0, SWT.MULTI | SWT.V_SCROLL, m_compoundModifyListener);
		m_documentation.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		return tabComposite;
	}

	private Control getMainTabControl(CTabFolder parent)
	{
		Composite tabComposite = EditorUtils.getNamedTabComposite(parent, "Main");

		Composite nameComposite = new Composite(tabComposite, SWT.NONE);
		GridLayout layout = new GridLayout(3, false);
		layout.marginRight = 8;
		layout.marginHeight = layout.marginWidth = 0;
		nameComposite.setLayout(layout);
		nameComposite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

		Label label = UiUtils.createGridLabel(nameComposite, "Component name:", 1, 0, SWT.NONE);
		int labelWidth = label.computeSize(SWT.DEFAULT, SWT.DEFAULT).x + 5;
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, false, false);
		gridData.widthHint = labelWidth;
		label.setLayoutData(gridData);

		m_componentName = UiUtils.createGridText(nameComposite, 2, 0, SWT.NONE, m_compoundModifyListener);

		UiUtils.createGridLabel(nameComposite, "Component Type:", 1, 0, SWT.NONE);
		m_componentType = UiUtils.createGridCombo(nameComposite, 1, 0, null, null, SWT.DROP_DOWN | SWT.READ_ONLY
				| SWT.SIMPLE);

		m_componentType.setItems(AbstractComponentType.getComponentTypeIDs(true));
		m_componentType.addModifyListener(m_compoundModifyListener);

		// not nice but I had to make equal 2 columns form different Composites
		// the purpose of hlpComposite is to create empty space, the same size
		// as m_componentCategory
		UiUtils.createEmptyPanel(nameComposite);

		int textWidth = m_componentType.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
		gridData = (GridData)m_componentType.getLayoutData();
		gridData.widthHint = textWidth;
		m_componentType.setLayoutData(gridData);

		Group versionGroup = new Group(tabComposite, SWT.NONE);
		versionGroup.setText("Version");
		layout = new GridLayout(3, false);
		versionGroup.setLayout(layout);
		versionGroup.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

		m_versionDesignator = new VersionDesignator(versionGroup);
		m_versionDesignator.addVersionDesignatorListener(m_compoundModifyListener);

		Control control = m_versionDesignator.getVersionDsTypeLabel();
		gridData = (GridData)control.getLayoutData();
		gridData.widthHint = labelWidth - layout.marginWidth - 3;
		control.setLayoutData(gridData);

		control = m_versionDesignator.getVersionDsTypeCombo();
		gridData = (GridData)control.getLayoutData();
		gridData.widthHint = textWidth;
		control.setLayoutData(gridData);

		Group propertiesGroup = new Group(tabComposite, SWT.NO_RADIO_GROUP);

		propertiesGroup.setText("Properties");
		layout = new GridLayout(2, false);
		propertiesGroup.setLayout(layout);
		propertiesGroup.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

		m_propertyURLCheckbox = UiUtils.createCheckButton(propertiesGroup, "Use Properties", new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				Button button = (Button)e.widget;

				if(!button.getSelection())
				{
					m_propertyURL.setText("");
				}
			}
		});
		gridData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		gridData.horizontalSpan = 2;
		m_propertyURLCheckbox.setLayoutData(gridData);

		label = UiUtils.createGridLabel(propertiesGroup, "Properties:", 1, labelWidth - layout.marginWidth - 3,
				SWT.NONE);

		Composite propertiesComposite = new Composite(propertiesGroup, SWT.NONE);

		layout = new GridLayout(2, false);
		layout.marginHeight = layout.marginWidth = 0;
		propertiesComposite.setLayout(layout);
		propertiesComposite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

		m_propertyURL = UiUtils.createGridText(propertiesComposite, 1, 0, SWT.NONE, m_compoundModifyListener);
		Button browseButton = new Button(propertiesComposite, SWT.PUSH);
		browseButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
		browseButton.setText("Browse...");
		browseButton.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent se)
			{
				FileDialog dlg = new FileDialog(getSite().getShell());
				dlg.setFilterExtensions(new String[] { "*.properties" });
				String name = dlg.open();
				if(name == null)
					return;
				try
				{
					m_propertyURL.setText(TextUtils.notNullString(new URL(name)));
				}
				catch(MalformedURLException e)
				{
					try
					{
						m_propertyURL.setText(TextUtils.notNullString(new File(name).toURI().toURL()));
					}
					catch(MalformedURLException e1)
					{
					}
				}
			}
		});

		m_propertyURLCheckbox.addSelectionListener(new CheckboxSelectionListener(new Control[] { label, m_propertyURL,
				browseButton }));

		Group rmapGroup = new Group(tabComposite, SWT.NO_RADIO_GROUP);
		rmapGroup.setText("Resource Map");
		layout = new GridLayout(2, false);
		rmapGroup.setLayout(layout);
		rmapGroup.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

		m_requestURLCheckbox = UiUtils.createCheckButton(rmapGroup, "Use Resource Map", new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				Button button = (Button)e.widget;

				if(!button.getSelection())
				{
					m_requestURL.setText("");
				}
			}
		});
		gridData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		gridData.horizontalSpan = 2;
		m_requestURLCheckbox.setLayoutData(gridData);

		label = UiUtils.createGridLabel(rmapGroup, "RMap URL:", 1, labelWidth - layout.marginWidth - 3, SWT.NONE);

		Composite rmapComposite = new Composite(rmapGroup, SWT.NONE);

		layout = new GridLayout(2, false);
		layout.marginHeight = layout.marginWidth = 0;
		rmapComposite.setLayout(layout);
		rmapComposite.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));

		m_requestURL = UiUtils.createGridText(rmapComposite, 1, 0, SWT.NONE, m_compoundModifyListener);
		browseButton = new Button(rmapComposite, SWT.PUSH);
		browseButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));
		browseButton.setText("Browse...");
		browseButton.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent se)
			{
				FileDialog dlg = new FileDialog(getSite().getShell());
				dlg.setFilterExtensions(new String[] { "*.rmap" });
				String name = dlg.open();
				if(name == null)
					return;
				try
				{
					m_requestURL.setText(TextUtils.notNullString(new URL(name)));
				}
				catch(MalformedURLException e)
				{
					try
					{
						m_requestURL.setText(TextUtils.notNullString(new File(name).toURI().toURL()));
					}
					catch(MalformedURLException e1)
					{
					}
				}
			}
		});

		m_requestURLCheckbox.addSelectionListener(new CheckboxSelectionListener(new Control[] { label, m_requestURL,
				browseButton }));

		return tabComposite;
	}

	private Control getPropertiesTabControl(Composite parent)
	{
		Composite tabComposite = EditorUtils.getNamedTabComposite(parent, "Properties");

		/*
		 * Group propertiesGroup = new Group(tabComposite, SWT.NONE); propertiesGroup.setText("Properties"); GridLayout
		 * layout = new GridLayout(1, false); propertiesGroup.setLayout(layout); propertiesGroup.setLayoutData(new
		 * GridData(GridData.FILL, GridData.FILL, true, true));
		 * 
		 * m_properties = UiUtils.createNoBorderGridText(propertiesGroup, 1, 0, compoundModifyListener, SWT.MULTI);
		 * m_properties.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		 */
		m_properties = new Properties(tabComposite, SWT.NONE);
		m_properties.addPropertiesModifyListener(m_compoundModifyListener);

		return tabComposite;
	}

	private AdvisorNodeBuilder getSelectedNode()
	{
		int idx = m_nodeTable.getTable().getSelectionIndex();
		return idx >= 0
				? (AdvisorNodeBuilder)m_nodeTable.getElementAt(idx)
				: null;
	}

	private void loadComponent(boolean materialize)
	{
		if(!commitChangesToQuery())
			return;

		try
		{
			ResolveJob resolveJob = new ResolveJob(m_componentQuery.createComponentQuery(), materialize, getSite(), m_continueOnError);
			resolveJob.schedule();
		}
		catch(CoreException e)
		{
			ErrorDialog.openError(getSite().getShell(), null, null, e.getStatus());
		}
	}

	private void newNode()
	{
		m_nodeTable.getTable().deselectAll();
		refreshNodeFields();
		editNode();
	}

	private void newOrSaveNode()
	{
		if(m_nodeEditMode)
			saveNode();
		else
			newNode();
	}

	private void nodeSelectionEvent()
	{
		enableDisableButtonGroup();
		refreshNodeFields();
	}

	private void refreshList()
	{
		m_nodeTable.setInput(m_componentQuery.getAdvisoryNodeList());
	}

	private void refreshNodeFields()
	{
		AdvisorNodeBuilder node = getSelectedNode();
		if(node == null)
			//
			// Use an empty node as template to get the defaults right.
			//
			node = new AdvisorNodeBuilder();

		m_allowCircular.setSelection(node.allowCircularDependency());
		m_namePattern.setText(TextUtils.notNullString(node.getNamePattern()));
		m_category.select(m_category.indexOf(TextUtils.notNullString(node.getComponentTypeID())));
		m_overlayFolder.setText(TextUtils.notNullString(node.getOverlayFolder()));
		m_wantedAttributes.setText(TextUtils.notNullString(TextUtils.concat(node.getAttributes(), ",")));
		m_prune.setSelection(node.isPrune());
		m_mutableLevel.select(m_mutableLevel.indexOf(node.getMutableLevel().toString()));
		m_sourceLevel.select(m_sourceLevel.indexOf(node.getSourceLevel().toString()));
		m_skipComponent.setSelection(node.skipComponent());
		m_useInstalled.setSelection(node.useInstalled());
		m_useMaterialization.setSelection(node.useMaterialization());
		m_useResolutionService.setSelection(node.isUseResolutionScheme());

		IVersionDesignator vs = node.getVersionOverride();
		boolean enableOverride = (vs != null);
		m_enableOverride.setSelection(enableOverride);
		m_versionOverride.setEnabled(enableOverride);
		m_versionOverride.refreshValues(vs);

		m_nodeProperties.setProperties(node.getProperties());
		m_nodeProperties.refreshList();

		Documentation doc = node.getDocumentation();
		m_nodeDocumentation.setText(TextUtils.notNullString(doc == null
				? null
				: doc.toString()));
	}

	private void refreshQuery()
	{
		setDirty(false);
		m_mute = true;
		try
		{
			ComponentRequest request = m_componentQuery.getRootRequest();
			m_componentName.setText(TextUtils.notNullString(request.getName()));
			m_componentType.select(m_componentType.indexOf(TextUtils.notNullString(request.getComponentTypeID())));
			m_versionDesignator.refreshValues(request.getVersionDesignator());

			String string = TextUtils.notNullString(m_componentQuery.getPropertiesURL());
			m_propertyURL.setText(string);
			m_propertyURLCheckbox.setSelection(string.length() > 0);
			m_propertyURLCheckbox.notifyListeners(SWT.Selection, new Event());

			string = TextUtils.notNullString(m_componentQuery.getResourceMapURL());
			m_requestURL.setText(string);
			m_requestURLCheckbox.setSelection(string.length() > 0);
			m_propertyURLCheckbox.notifyListeners(SWT.Selection, new Event());
			m_properties.setProperties(m_componentQuery.getProperties());
			m_shortDesc.setText(TextUtils.notNullString(m_componentQuery.getShortDesc()));
			Documentation doc = m_componentQuery.getDocumentation();
			m_documentation.setText(TextUtils.notNullString(doc == null
					? ""
					: doc.toString()));
			refreshList();
			m_properties.refreshList();
			m_needsRefresh = false;
			nodeSelectionEvent();
		}
		finally
		{
			m_mute = false;
		}
	}

	private void removeNode()
	{
		AdvisorNodeBuilder node = getSelectedNode();
		if(node != null)
		{
			m_componentQuery.removeAdvisorNode(node);
			setDirty(true);
			refreshList();
		}
	}

	private boolean saveNode()
	{
		AdvisorNodeBuilder node = getSelectedNode();
		boolean isNewNode = false;
		if(node == null)
		{
			node = new AdvisorNodeBuilder();
			isNewNode = true;
		}

		boolean refreshListNeeded = false;
		String patternStr = UiUtils.trimmedValue(m_namePattern);
		String category = m_category.getItem(m_category.getSelectionIndex());
		if(category.length() == 0)
			category = null;

		if(patternStr == null)
		{
			MessageDialog.openError(getSite().getShell(), null, "The name pattern cannot be empty");
			return false;
		}
		Pattern pattern;
		try
		{
			pattern = Pattern.compile(patternStr);
		}
		catch(PatternSyntaxException e)
		{
			MessageDialog.openError(getSite().getShell(), null, e.getMessage());
			return false;
		}

		String currentCategory = node.getComponentTypeID();
		Pattern currentPattern = node.getNamePattern();
		if(currentPattern == null || !currentPattern.toString().equals(patternStr)
				|| !Trivial.equalsAllowNull(currentCategory, category))
		{
			// Pattern changed. Verify that it's not a duplicate
			//
			AdvisorNodeBuilder patternEqual = m_componentQuery.getNodeByPattern(patternStr, category);
			if(patternEqual != null)
			{
				if(!MessageDialog.openQuestion(getSite().getShell(), null, "Overwrite existing node with same pattern"))
					return false;
				m_componentQuery.removeAdvisorNode(patternEqual);
			}
			refreshListNeeded = true;
		}
		node.setNamePattern(pattern);
		node.setComponentTypeID(category);
		node.setAllowCircularDependency(m_allowCircular.getSelection());

		boolean override = m_enableOverride.getSelection();
		IVersionDesignator versionOverride = null;
		if(override)
			versionOverride = m_versionOverride.getVersionDesignator();

		try
		{
			String tmp = UiUtils.trimmedValue(m_overlayFolder);
			node.setOverlayFolder(tmp == null
					? null
					: URLUtils.normalizeToURL(tmp));
		}
		catch(Exception e)
		{
			MessageDialog.openError(getSite().getShell(), null, e.getMessage());
			return false;
		}

		node.setSkipComponent(m_skipComponent.getSelection());

		String tmp = UiUtils.trimmedValue(m_wantedAttributes);
		if(tmp != null)
			for(String attribute : tmp.split(","))
				node.addAttribute(attribute);
		node.setPrune(m_prune.getSelection());

		int idx = m_mutableLevel.getSelectionIndex();
		node.setMutableLevel(idx >= 0
				? MutableLevel.values()[idx]
				: null);

		idx = m_sourceLevel.getSelectionIndex();
		node.setSourceLevel(idx >= 0
				? SourceLevel.values()[idx]
				: null);

		node.setUseInstalled(m_useInstalled.getSelection());
		node.setUseMaterialization(m_useMaterialization.getSelection());
		node.setUseResolutionScheme(m_useResolutionService.getSelection());

		node.setVersionOverride(versionOverride);

		m_nodeProperties.fillProperties(node.getProperties());

		String doc = UiUtils.trimmedValue(m_nodeDocumentation);
		
		try
		{
			node.setDocumentation(doc == null ? null : Documentation.parse(doc));
		}
		catch(Exception e)
		{
			MessageDialog.openError(getSite().getShell(), null, e.getMessage());
			return false;
		}

		if(isNewNode)
		{
			// This was an add operation
			//
			m_componentQuery.addAdvisorNode(node);
			refreshListNeeded = true;
		}
		if(refreshListNeeded)
			refreshList();

		setDirty(true);
		m_nodeEditMode = false;
		enableDisableButtonGroup();
		return true;
	}

	private void saveToPath(IPath path)
	{
		try
		{
			SaveRunnable sr = new SaveRunnable(m_componentQuery.createComponentQuery(), path);
			getSite().getWorkbenchWindow().run(true, true, sr);
			setInputWithNotify(sr.getSavedInput());
			setDirty(false);
			setPartName(path.lastSegment());
			firePropertyChange(IWorkbenchPart.PROP_TITLE);
		}
		catch(InvocationTargetException e)
		{
			CoreException t = BuckminsterException.wrap(e);
			String msg = "Unable to save file " + path;
			CorePlugin.getLogger().error(msg, t);
			ErrorDialog.openError(getSite().getShell(), null, msg, t.getStatus());
		}
		catch(InterruptedException e)
		{
		}
	}

	private void setDirty(boolean flag)
	{
		if(m_mute || m_hasChanges == flag)
			return;
		m_hasChanges = flag;
		m_externalSaveAsButton.setEnabled(flag);
		firePropertyChange(PROP_DIRTY);
	}

	private void swapAndReselect(int idxOffset, int selectionOffset)
	{
		Table table = m_nodeTable.getTable();
		int idx = table.getSelectionIndex() + idxOffset;
		if(idx <= 0)
			return;

		List<AdvisorNodeBuilder> nl = m_componentQuery.getAdvisoryNodeList();
		if(idx >= nl.size())
			return;

		nl.set(idx - 1, nl.set(idx, nl.get(idx - 1)));
		refreshList();
		table.select(idx + selectionOffset);
		enableDisableButtonGroup();
		setDirty(true);
	}
}
