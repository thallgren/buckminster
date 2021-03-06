/*******************************************************************************
 * Copyright (c) 2006-2013, Cloudsmith Inc.
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 ******************************************************************************/

package org.eclipse.buckminster.core.rmap.model;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import org.eclipse.buckminster.core.CorePlugin;
import org.eclipse.buckminster.core.common.model.ExpandingProperties;
import org.eclipse.buckminster.core.common.model.RxAssembly;
import org.eclipse.buckminster.core.common.model.RxPart;
import org.eclipse.buckminster.core.common.model.TaggedRxPattern;
import org.eclipse.buckminster.core.cspec.builder.CSpecBuilder;
import org.eclipse.buckminster.core.cspec.model.ComponentRequest;
import org.eclipse.buckminster.core.ctype.IComponentType;
import org.eclipse.buckminster.core.helpers.DateAndTimeUtils;
import org.eclipse.buckminster.core.helpers.FilterUtils;
import org.eclipse.buckminster.core.metadata.builder.ResolutionBuilder;
import org.eclipse.buckminster.core.metadata.model.Resolution;
import org.eclipse.buckminster.core.reader.URLCatalogReaderType;
import org.eclipse.buckminster.core.resolver.NodeQuery;
import org.eclipse.buckminster.core.version.ProviderMatch;
import org.eclipse.buckminster.core.version.VersionHelper;
import org.eclipse.buckminster.core.version.VersionMatch;
import org.eclipse.buckminster.core.version.VersionSelector;
import org.eclipse.buckminster.download.DownloadManager;
import org.eclipse.buckminster.osgi.filter.Filter;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.IFileInfo;
import org.eclipse.buckminster.runtime.Logger;
import org.eclipse.buckminster.runtime.URLUtils;
import org.eclipse.buckminster.sax.Utils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.p2.metadata.IVersionFormat;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @author Thomas Hallgren
 * 
 */
public class URIMatcher extends RxAssembly {
	@SuppressWarnings("hiding")
	public static final String TAG = "matcher"; //$NON-NLS-1$

	public static final String ATTR_BASE = "base"; //$NON-NLS-1$

	public static final String ATTR_VERSION_FORMAT = "versionFormat"; //$NON-NLS-1$

	public static final String ATTR_VERSION_TYPE = "versionType"; //$NON-NLS-1$

	public static final String COMPONENT_NAME_PARAM = TaggedRxPattern.TAGGED_PREFIX + "name"; //$NON-NLS-1$

	public static final String COMPONENT_VERSION_PARAM = TaggedRxPattern.TAGGED_PREFIX + "version"; //$NON-NLS-1$

	public static final String BRANCH_PARAM = TaggedRxPattern.TAGGED_PREFIX + "branch"; //$NON-NLS-1$

	public static final String TAG_PARAM = TaggedRxPattern.TAGGED_PREFIX + "tag"; //$NON-NLS-1$

	public static final String REVISION_PARAM = TaggedRxPattern.TAGGED_PREFIX + "revision"; //$NON-NLS-1$

	public static final String TIMESTAMP_PARAM = TaggedRxPattern.TAGGED_PREFIX + "timestamp"; //$NON-NLS-1$

	public static final String OS_PARAM = TaggedRxPattern.TAGGED_PREFIX + "os"; //$NON-NLS-1$

	public static final String WS_PARAM = TaggedRxPattern.TAGGED_PREFIX + "ws"; //$NON-NLS-1$

	public static final String ARCH_PARAM = TaggedRxPattern.TAGGED_PREFIX + "arch"; //$NON-NLS-1$

	public static final String NL_PARAM = TaggedRxPattern.TAGGED_PREFIX + "nl"; //$NON-NLS-1$

	public static final String ARTIFACT_INFO_PREFIX = "URIMetaData:"; //$NON-NLS-1$

	private static Filter getFilter(Map<String, String> matchMap) {
		return FilterUtils.createFilter(matchMap.get(OS_PARAM), matchMap.get(WS_PARAM), matchMap.get(ARCH_PARAM), matchMap.get(NL_PARAM));
	}

	private final String base;

	private final IVersionFormat versionFormat;

	private final String componentType;

	public URIMatcher(List<RxPart> parts, String base, IVersionFormat versionFormat, String componentType) throws CoreException,
			PatternSyntaxException {
		super(parts);
		this.base = base;
		this.versionFormat = versionFormat == null ? VersionHelper.getOSGiFormat() : versionFormat;
		this.componentType = componentType;
	}

	public Resolution createResolution(ProviderMatch pm) throws CoreException {
		Map<String, String> matchMap = pm.getMatcherMap();
		if (matchMap == null)
			return null;

		CSpecBuilder bld = new CSpecBuilder();
		bld.setName(matchMap.get(COMPONENT_NAME_PARAM));
		String tmp = matchMap.get(COMPONENT_VERSION_PARAM);
		if (tmp != null) {
			try {
				bld.setVersion(versionFormat.parse(tmp));
			} catch (IllegalArgumentException e) {
				throw BuckminsterException.wrap(e);
			}
		}

		IComponentType ctype = pm.getComponentType();
		bld.setComponentTypeID(ctype.getId());
		bld.setFilter(getFilter(matchMap));

		try {
			IFileInfo info = DownloadManager.readInfo(URLUtils.normalizeToURL(pm.getRepositoryURI()), pm.getConnectContext());
			NodeQuery nq = pm.getNodeQuery();
			ResolutionBuilder resBld = new ResolutionBuilder(bld);
			resBld.getRequest().initFrom(nq.getComponentRequest());
			resBld.setAttributes(nq.getRequiredAttributes());
			resBld.setProvider(pm.getProvider());
			resBld.setRepository(pm.getProvider().getURI(nq.getProperties()));
			resBld.setComponentTypeId(ctype.getId());
			resBld.setVersionMatch(pm.getVersionMatch());
			resBld.setFileInfo(info);
			return new Resolution(resBld);
		} catch (FileNotFoundException e) {
			return null;
		} catch (MalformedURLException e) {
			throw BuckminsterException.wrap(e);
		}
	}

	public String getBase() {
		return base;
	}

	@Override
	public String getDefaultTag() {
		return TAG;
	}

	public ProviderMatch getMatch(Provider provider, NodeQuery query, IProgressMonitor monitor) throws CoreException {
		Logger logger = CorePlugin.getLogger();
		ComponentRequest cq = query.getComponentRequest();
		VersionMatch candidate = null;
		Map<String, String> candidateMap = null;

		Map<String, ? extends Object> providerProperties = provider.getProperties(query.getProperties());
		URL baseURL;
		try {
			baseURL = new URL(ExpandingProperties.expand(providerProperties, base, 0));
		} catch (MalformedURLException e) {
			throw BuckminsterException.wrap(e);
		}

		URL[] urlsToMatch = URLCatalogReaderType.list(baseURL, query.getComponentQuery().getConnectContext(), monitor);
		if (urlsToMatch.length == 0) {
			logger.debug("No URL's for %s found on page %s", cq.getName(), baseURL); //$NON-NLS-1$
			return null;
		}

		for (URL urlToMatch : urlsToMatch) {
			Map<String, String> matchMap = getMatchMap(urlToMatch.toString());
			if (matchMap == null)
				continue;

			String matchedName = matchMap.get(COMPONENT_NAME_PARAM);
			if (!cq.getName().equals(matchedName)) {
				logger.debug("URI name %s does not match %s", matchedName, cq.getName()); //$NON-NLS-1$
				continue;
			}

			Version version = null;
			String tmp = matchMap.get(COMPONENT_VERSION_PARAM);
			if (tmp != null) {
				try {
					version = versionFormat.parse(tmp);
					VersionRange vd = cq.getVersionRange();
					if (!(vd == null || vd.isIncluded(version))) {
						logger.debug("URI version %s is not designated by %s", version, vd); //$NON-NLS-1$
						continue;
					}
				} catch (IllegalArgumentException e) {
					logger.warning(e, e.getMessage());
					continue;
				}
			}

			Filter filter = getFilter(matchMap);
			if (!(filter == null || filter.matchCase(providerProperties))) {
				logger.debug("URI filter %s does not match current environment", filter); //$NON-NLS-1$
				continue;
			}

			VersionSelector vs = null;
			tmp = matchMap.get(BRANCH_PARAM);
			if (tmp != null)
				vs = VersionSelector.branch(tmp);
			else {
				tmp = matchMap.get(TAG_PARAM);
				if (tmp != null)
					vs = VersionSelector.tag(tmp);
			}

			long revision = -1;
			tmp = matchMap.get(REVISION_PARAM);
			if (tmp != null) {
				try {
					revision = Long.parseLong(tmp);
				} catch (NumberFormatException e) {
					logger.warning(e, e.getMessage());
				}
			}

			Date timestamp = null;
			tmp = matchMap.get(TIMESTAMP_PARAM);
			if (tmp != null) {
				try {
					timestamp = DateAndTimeUtils.fromString(tmp);
				} catch (ParseException e) {
					logger.warning(e, e.getMessage());
				}
			}

			VersionMatch vm = new VersionMatch(version, vs, revision, timestamp, null);
			if (candidate == null || query.compare(vm, candidate) > 0) {
				// Verify that the URI created using this matchMap is readable
				//
				candidate = vm;
				candidateMap = matchMap;
			}
		}
		if (candidate == null)
			return null;

		query = query.getContext().getNodeQuery(query.getQualifiedDependency());
		query = new NodeQuery(query, candidateMap);
		ProviderMatch pm = new ProviderMatch(provider, CorePlugin.getDefault().getComponentType(componentType), candidate, query);
		pm.setMatcherMap(candidateMap);
		return pm;
	}

	public IVersionFormat getVersionType() {
		return versionFormat;
	}

	@Override
	protected void addAttributes(AttributesImpl attrs) throws SAXException {
		super.addAttributes(attrs);
		Utils.addAttribute(attrs, ATTR_BASE, base);
		if (!versionFormat.equals(VersionHelper.getOSGiFormat()))
			Utils.addAttribute(attrs, ATTR_VERSION_FORMAT, versionFormat.toString());
	}
}
