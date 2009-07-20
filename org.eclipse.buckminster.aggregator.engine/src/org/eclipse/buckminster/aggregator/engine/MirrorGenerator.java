package org.eclipse.buckminster.aggregator.engine;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.buckminster.aggregator.Aggregator;
import org.eclipse.buckminster.aggregator.Contribution;
import org.eclipse.buckminster.aggregator.MappedRepository;
import org.eclipse.buckminster.aggregator.PackedStrategy;
import org.eclipse.buckminster.aggregator.p2.ArtifactKey;
import org.eclipse.buckminster.aggregator.p2.InstallableUnit;
import org.eclipse.buckminster.aggregator.p2.MetadataRepository;
import org.eclipse.buckminster.aggregator.p2.RepositoryReference;
import org.eclipse.buckminster.runtime.Buckminster;
import org.eclipse.buckminster.runtime.BuckminsterException;
import org.eclipse.buckminster.runtime.Logger;
import org.eclipse.buckminster.runtime.MonitorUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository;
import org.eclipse.equinox.internal.p2.artifact.repository.MirrorRequest;
import org.eclipse.equinox.internal.p2.artifact.repository.RawMirrorRequest;
import org.eclipse.equinox.internal.p2.core.helpers.FileUtils;
import org.eclipse.equinox.internal.p2.director.PermissiveSlicer;
import org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.ArtifactDescriptor;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactDescriptor;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactRepository;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.IArtifactRepositoryManager;
import org.eclipse.equinox.internal.provisional.p2.artifact.repository.processing.ProcessingStepHandler;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.metadata.IArtifactKey;
import org.eclipse.equinox.internal.provisional.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepository;
import org.eclipse.equinox.internal.provisional.p2.metadata.repository.IMetadataRepositoryManager;
import org.eclipse.equinox.internal.provisional.p2.query.Collector;
import org.eclipse.equinox.internal.provisional.p2.query.IQueryable;
import org.eclipse.equinox.internal.provisional.p2.query.MatchQuery;
import org.eclipse.equinox.internal.provisional.p2.query.Query;
import org.eclipse.equinox.internal.provisional.p2.repository.IRepository;
import org.eclipse.equinox.p2.publisher.Publisher;

public class MirrorGenerator extends BuilderPhase
{
	/**
	 * A request to restore the canonical form after a raw copy of the optimized form
	 */
	private static class CanonicalizeRequest extends MirrorRequest
	{
		private IArtifactDescriptor optimizedDescriptor;

		private IArtifactDescriptor canonicalDescriptor;

		public CanonicalizeRequest(IArtifactDescriptor optimizedDescriptor, IArtifactDescriptor canonicalDescriptor,
				IArtifactRepository targetRepository)
		{
			super(canonicalDescriptor.getArtifactKey(), targetRepository, null, null);
			this.optimizedDescriptor = optimizedDescriptor;
			this.canonicalDescriptor = canonicalDescriptor;
			setSourceRepository(targetRepository);
		}

		@Override
		public void perform(IProgressMonitor monitor)
		{
			setResult(transfer(canonicalDescriptor, optimizedDescriptor, monitor));
		}
	}

	private static IStatus extractDeeperRootCause(IStatus status)
	{
		if(status == null)
			return null;

		if(status.isMultiStatus())
		{
			IStatus[] children = ((MultiStatus)status).getChildren();
			for(int i = 0; i < children.length; i++)
			{
				IStatus deeper = extractDeeperRootCause(children[i]);
				if(deeper != null)
					return deeper;
			}
		}

		Throwable t = status.getException();
		if(t instanceof CoreException)
		{
			IStatus deeper = extractDeeperRootCause(((CoreException)t).getStatus());
			if(deeper != null)
				return deeper;
		}
		return status.getSeverity() == IStatus.ERROR
				? status
				: null;
	}

	/**
	 * Extract the root cause. The root cause is the first severe non-MultiStatus status containing an exception when
	 * searching depth first otherwise null.
	 * 
	 * @param status
	 * @return root cause
	 */
	private static IStatus extractRootCause(IStatus status)
	{
		IStatus rootCause = extractDeeperRootCause(status);
		return rootCause == null
				? status
				: rootCause;
	}

	private static void mirror(IArtifactRepository source, IArtifactRepository dest, IArtifactDescriptor sourceDesc,
			IArtifactDescriptor targetDesc, IProgressMonitor monitor) throws CoreException
	{
		if(dest.contains(sourceDesc))
			return;

		RawMirrorRequest request = new RawMirrorRequest(sourceDesc, targetDesc, dest);
		request.setSourceRepository(source);
		request.perform(monitor);
		IStatus result = request.getResult();
		if(result.getSeverity() == IStatus.ERROR)
		{
			if(result.getCode() != org.eclipse.equinox.internal.provisional.p2.core.ProvisionException.ARTIFACT_EXISTS)
			{
				dest.removeDescriptor(sourceDesc);
				result = extractRootCause(result);
				throw BuckminsterException.fromMessage(result.getException(),
						"Unable to mirror artifact %s from repository %s: %s", sourceDesc.getArtifactKey(),
						source.getLocation(), result.getMessage());
			}
		}
	}

	private static void unpackToSibling(IArtifactRepository target, IArtifactDescriptor optimized,
			IArtifactDescriptor canonical, boolean verifyOnly, IProgressMonitor monitor) throws CoreException
	{
		CanonicalizeRequest request = new CanonicalizeRequest(optimized, canonical, target);
		request.perform(monitor);
		IStatus result = request.getResult();
		if(result.getSeverity() != IStatus.ERROR
				|| result.getCode() == org.eclipse.equinox.internal.provisional.p2.core.ProvisionException.ARTIFACT_EXISTS)
		{
			if(verifyOnly)
				target.removeDescriptor(canonical);
			return;
		}

		result = extractRootCause(result);
		target.removeDescriptor(canonical);
		throw BuckminsterException.fromMessage(result.getException(),
				"Unable to unpack artifact %s in repository %s: %s", optimized.getArtifactKey(), target.getLocation(),
				result.getMessage());
	}

	private IMetadataRepositoryManager mdrMgr = null;

	private IArtifactRepositoryManager arMgr = null;

	public MirrorGenerator(Builder builder)
	{
		super(builder);
	}

	public void addUnverifiedRoots(IProgressMonitor monitor) throws CoreException
	{
		// Add the transitive closure of all unverified roots
		//
		Builder builder = getBuilder();
		Buckminster bucky = Buckminster.getDefault();
		Set<IInstallableUnit> unverifiedRoots = builder.getUnverifiedUnits();
		if(unverifiedRoots.isEmpty())
			return;

		final Set<IInstallableUnit> unitsToAggregate = builder.getUnverifiedUnits();
		try
		{
			IMetadataRepository sourceMdr = builder.getSourceComposite();
			PermissiveSlicer slicer = new PermissiveSlicer(sourceMdr, null, true, false, true, false, false);
			IQueryable slice = slicer.slice(unverifiedRoots.toArray(new IInstallableUnit[unverifiedRoots.size()]),
					monitor);

			Query adder = new MatchQuery()
			{
				@Override
				public boolean isMatch(Object candidate)
				{
					unitsToAggregate.add((IInstallableUnit)candidate);
					return false;
				}
			};
			slice.query(adder, new Collector(), null);
		}
		finally
		{
			bucky.ungetService(mdrMgr);
		}
	}

	public Set<IArtifactKey> getArtifactKeysToExclude()
	{
		Builder builder = getBuilder();
		Aggregator aggregator = builder.getAggregator();

		HashSet<IArtifactKey> keysToExclude = new HashSet<IArtifactKey>();
		List<Contribution> contribs = aggregator.getContributions();
		for(Contribution contrib : contribs)
		{
			for(MappedRepository repo : contrib.getRepositories())
			{
				if(repo.isMirrorArtifacts())
					continue;

				for(InstallableUnit iu : repo.getMetadataRepository().getInstallableUnits())
					keysToExclude.addAll(iu.getArtifactList());
			}
		}
		return keysToExclude;
	}

	@Override
	public void run(IProgressMonitor monitor) throws CoreException
	{
		Logger log = Buckminster.getLogger();
		log.info("Starting mirror generation");
		long now = System.currentTimeMillis();

		Builder builder = getBuilder();
		File destination = new File(builder.getBuildRoot(), Builder.REPO_FOLDER_FINAL);
		URI finalURI = Builder.createURI(destination);

		File aggregateDestination = new File(destination, Builder.REPO_FOLDER_AGGREGATE);
		URI aggregateURI = Builder.createURI(aggregateDestination);

		Buckminster bucky = Buckminster.getDefault();

		mdrMgr = bucky.getService(IMetadataRepositoryManager.class);
		arMgr = bucky.getService(IArtifactRepositoryManager.class);
		MonitorUtils.begin(monitor, 1000);
		boolean artifactErrors = false;
		try
		{
			boolean isUpdate = builder.isUpdate();
			Aggregator aggregator = builder.getAggregator();

			MonitorUtils.subTask(monitor, "Initializing mirroring");
			IArtifactRepository aggregateAr = null;
			IMetadataRepository aggregateMdr = null;
			if(!isUpdate)
			{
				FileUtils.deleteAll(destination);
				mdrMgr.removeRepository(finalURI);
				arMgr.removeRepository(aggregateURI);
				MonitorUtils.worked(monitor, 10);
			}
			else
			{
				try
				{
					aggregateAr = arMgr.loadRepository(aggregateURI, MonitorUtils.subMonitor(monitor, 5));
				}
				catch(ProvisionException e)
				{
				}

				try
				{
					aggregateMdr = mdrMgr.loadRepository(aggregateURI, MonitorUtils.subMonitor(monitor, 5));
				}
				catch(ProvisionException e)
				{
				}
			}

			if(aggregateAr == null)
			{
				Map<String, String> properties = new HashMap<String, String>();
				properties.put(IRepository.PROP_COMPRESSED, Boolean.toString(true));
				properties.put(Publisher.PUBLISH_PACK_FILES_AS_SIBLINGS, Boolean.toString(true));
				String label = builder.getAggregator().getLabel();
				aggregateAr = arMgr.createRepository(aggregateURI,
						label + " artifacts", Builder.SIMPLE_ARTIFACTS_TYPE, properties); //$NON-NLS-1$
			}
			MonitorUtils.worked(monitor, 5);

			if(aggregateMdr == null)
			{
				Map<String, String> properties = new HashMap<String, String>();
				properties.put(IRepository.PROP_COMPRESSED, Boolean.toString(true));
				String label = aggregator.getLabel();
				aggregateMdr = mdrMgr.createRepository(aggregateURI, label, Builder.SIMPLE_METADATA_TYPE, properties);
			}
			MonitorUtils.worked(monitor, 5);

			addUnverifiedRoots(MonitorUtils.subMonitor(monitor, 5));
			Set<IInstallableUnit> unitsToAggregate = builder.getUnitsToAggregate();
			Set<IArtifactKey> keysToExclude = getArtifactKeysToExclude();

			IProgressMonitor childMonitor = MonitorUtils.subMonitor(monitor, 900);
			List<Contribution> contribs = aggregator.getContributions();
			MonitorUtils.begin(childMonitor, contribs.size() * 100);
			boolean aggregatedMdrIsEmpty = true;
			boolean aggregatedArIsEmpty = true;
			for(Contribution contrib : contribs)
			{
				IProgressMonitor contribMonitor = MonitorUtils.subMonitor(childMonitor, 100);
				List<MappedRepository> repos = contrib.getRepositories();
				List<String> errors = new ArrayList<String>();
				MonitorUtils.begin(contribMonitor, repos.size() * 100);
				for(MappedRepository repo : repos)
				{
					if(builder.isMapVerbatim(repo))
					{
						MonitorUtils.worked(contribMonitor, 100);
						continue;
					}

					MetadataRepository childMdr = repo.getMetadataRepository();
					ArrayList<InstallableUnit> iusToMirror = null;
					ArrayList<ArtifactKey> keysToMirror = null;
					for(InstallableUnit iu : childMdr.getInstallableUnits())
					{
						if(!unitsToAggregate.remove(iu))
							continue;

						if(iusToMirror == null)
							iusToMirror = new ArrayList<InstallableUnit>();
						iusToMirror.add(iu);
						if(!repo.isMirrorArtifacts())
							continue;

						for(ArtifactKey ak : iu.getArtifactList())
						{
							if(!keysToExclude.add(ak))
								continue;

							if(keysToMirror == null)
								keysToMirror = new ArrayList<ArtifactKey>();
							keysToMirror.add(ak);
						}
					}

					if(iusToMirror != null)
					{
						String msg = String.format("Mirroring meta-data from from %s", childMdr.getLocation());
						log.info(msg);
						contribMonitor.subTask(msg);
						IProgressMonitor repoMonitor = MonitorUtils.subMonitor(contribMonitor, 5);
						mirror(iusToMirror, childMdr, aggregateMdr, repoMonitor);
						aggregatedMdrIsEmpty = false;
					}
					else
						MonitorUtils.worked(contribMonitor, 5);

					if(keysToMirror != null)
					{
						String msg = String.format("Mirroring artifacts from from %s", childMdr.getLocation());
						log.info(msg);
						contribMonitor.subTask(msg);
						IArtifactRepository childAr = arMgr.loadRepository(childMdr.getLocation(),
								MonitorUtils.subMonitor(childMonitor, 1));
						IProgressMonitor repoMonitor = MonitorUtils.subMonitor(contribMonitor, 95);
						mirror(keysToMirror, childAr, aggregateAr, errors, repoMonitor);
						aggregatedArIsEmpty = false;
					}
					else
						MonitorUtils.worked(contribMonitor, 95);
				}
				if(errors.size() > 0)
					builder.sendEmail(contrib, errors);
				MonitorUtils.done(contribMonitor);
			}

			List<InstallableUnit> categories = builder.getCategoryIUs();
			if(!categories.isEmpty())
			{
				mirror(categories, null, aggregateMdr, MonitorUtils.subMonitor(childMonitor, 20));
				aggregatedMdrIsEmpty = false;
			}

			new File(destination, "compositeArtifacts.jar").delete();
			new File(destination, "compositeContent.jar").delete();
			new File(destination, "content.jar").delete();
			new File(destination, "artifacts.jar").delete();
			MonitorUtils.worked(childMonitor, 10);

			List<MappedRepository> reposWithReferencedArtifacts = new ArrayList<MappedRepository>();
			List<MappedRepository> reposWithReferencedMetadata = new ArrayList<MappedRepository>();

			for(Contribution contrib : aggregator.getContributions())
			{
				for(MappedRepository repo : contrib.getRepositories())
				{
					if(builder.isMapVerbatim(repo))
					{
						reposWithReferencedArtifacts.add(repo);
						reposWithReferencedMetadata.add(repo);
					}
					else if(!repo.isMirrorArtifacts())
						reposWithReferencedArtifacts.add(repo);
				}
			}

			if(reposWithReferencedMetadata.isEmpty())
			{
				// The aggregated meta-data can serve as the final repository so
				// let's move it.
				//
				log.info("Making the aggregated metadata repository final at %s", finalURI);
				File oldLocation = new File(aggregateDestination, "content.jar");
				File newLocation = new File(destination, oldLocation.getName());
				oldLocation.renameTo(newLocation);
				mdrMgr.removeRepository(aggregateURI);
			}
			else
			{
				// Set up the final composite repositories
				log.info("Building final metadata composite at %s", finalURI);
				Map<String, String> properties = new HashMap<String, String>();
				properties.put(IRepository.PROP_COMPRESSED, Boolean.toString(true));

				String name = builder.getAggregator().getLabel();
				mdrMgr.removeRepository(finalURI);
				CompositeMetadataRepository compositeMdr = (CompositeMetadataRepository)mdrMgr.createRepository(
						finalURI, name, Builder.COMPOSITE_METADATA_TYPE, properties); //$NON-NLS-1$

				for(MappedRepository referenced : reposWithReferencedArtifacts)
					compositeMdr.addChild(referenced.getMetadataRepository().getLocation());

				if(aggregatedMdrIsEmpty)
				{
					mdrMgr.removeRepository(aggregateURI);
					File mdrFile = new File(aggregateDestination, "content.jar");
					mdrFile.delete();
				}
				else
					compositeMdr.addChild(finalURI.relativize(aggregateURI));

				log.info("Done building final metadata composite");
			}
			MonitorUtils.worked(childMonitor, 10);

			if(reposWithReferencedArtifacts.isEmpty())
			{
				// The aggregation can serve as the final repository.
				//
				log.info("Making the aggregated artifact repository final at %s", finalURI);
				for(String name : aggregateDestination.list())
				{
					if("content.jar".equals(name))
						continue;

					File oldLocation = new File(aggregateDestination, name);
					File newLocation = new File(destination, name);
					oldLocation.renameTo(newLocation);
					aggregateDestination.delete();
				}
				arMgr.removeRepository(aggregateURI);
			}
			else
			{
				// Set up the final composite repositories
				log.info("Building final artifact composite at %s", finalURI);
				Map<String, String> properties = new HashMap<String, String>();
				properties.put(IRepository.PROP_COMPRESSED, Boolean.toString(true));

				String name = builder.getAggregator().getLabel();
				arMgr.removeRepository(finalURI);
				CompositeArtifactRepository compositeAr = (CompositeArtifactRepository)arMgr.createRepository(finalURI,
						name + " artifacts", Builder.COMPOSITE_ARTIFACTS_TYPE, properties); //$NON-NLS-1$

				for(MappedRepository referenced : reposWithReferencedArtifacts)
					compositeAr.addChild(referenced.getMetadataRepository().getLocation());

				if(aggregatedArIsEmpty)
				{
					arMgr.removeRepository(aggregateURI);
					File arFile = new File(aggregateDestination, "artifacts.jar");
					arFile.delete();
				}
				else
					compositeAr.addChild(finalURI.relativize(aggregateURI));

				log.info("Done building final artifact composite");
			}

			// Remove the aggregation in case it's now empty.
			//
			String[] content = aggregateDestination.list();
			if(content != null && content.length == 0)
				aggregateDestination.delete();

			MonitorUtils.done(childMonitor);
		}
		finally
		{
			bucky.ungetService(mdrMgr);
			mdrMgr = null;
			bucky.ungetService(arMgr);
			arMgr = null;
			MonitorUtils.done(monitor);
		}
		log.info("Done. Took %d ms", Long.valueOf(System.currentTimeMillis() - now));
		if(artifactErrors)
			throw BuckminsterException.fromMessage("Not all artifacts could be mirrored");
	}

	private void mirror(List<ArtifactKey> keysToInstall, IArtifactRepository source, IArtifactRepository dest,
			List<String> errors, IProgressMonitor monitor)
	{
		Logger log = Buckminster.getLogger();
		IArtifactKey[] keys = source.getArtifactKeys();
		MonitorUtils.begin(monitor, keys.length * 100);

		for(IArtifactKey key : keys)
		{
			if(!keysToInstall.contains(key))
				continue;

			log.info("- mirroring artifact %s", key);

			PackedStrategy strategy = getBuilder().getAggregator().getPackedStrategy();
			if(!"osgi.bundle".equals(key.getClassifier()))
				strategy = PackedStrategy.SKIP;

			try
			{
				IArtifactDescriptor[] aDescs = source.getArtifactDescriptors(key);
				// Typically one that has no format and one that is packed.
				// If so,
				// just copy the packed one.
				//
				IArtifactDescriptor optimized = null;
				IArtifactDescriptor canonical = null;
				for(IArtifactDescriptor desc : aDescs)
				{
					if(desc.getProperty(IArtifactDescriptor.FORMAT) == null)
						canonical = desc;
					else if(ProcessingStepHandler.canProcess(desc))
						optimized = desc;
				}

				if(optimized == null && canonical == null)
					throw BuckminsterException.fromMessage(
							"Found no usable descriptor for artifact %s in repository %s", key, dest.getLocation());

				if(optimized == null)
				{
					log.debug("    doing copy of canonical artifact");
					mirror(source, dest, canonical, canonical, MonitorUtils.subMonitor(monitor, 90));
					continue;
				}

				switch(strategy)
				{
				case SKIP:
					if(canonical == null)
						// Canonical is required
						throw BuckminsterException.fromMessage("No canonical artifact %s found in repository %s", key,
								dest.getLocation());
					log.debug("    doing copy of canonical artifact");
					mirror(source, dest, canonical, canonical, MonitorUtils.subMonitor(monitor, 90));
				case COPY:
					log.debug("    doing copy of optimized artifact");
					mirror(source, dest, optimized, optimized, MonitorUtils.subMonitor(monitor, 90));
					break;
				default:
					// We need a canonical descriptor to complete this.
					if(canonical == null)
					{
						ArtifactDescriptor ad = new ArtifactDescriptor(key);
						ad.setRepository(dest);
						canonical = ad;
					}
					if(strategy == PackedStrategy.UNPACK)
					{
						log.debug("    doing copy of optimized artifact into canonical target");
						mirror(source, dest, optimized, canonical, MonitorUtils.subMonitor(monitor, 90));
					}
					else
					{
						log.debug("    doing copy of optimized artifact");
						mirror(source, dest, optimized, optimized, MonitorUtils.subMonitor(monitor, 70));
						boolean isVerify = strategy == PackedStrategy.VERIFY;
						log.debug("    unpacking optimized artifact%s", isVerify
								? " for verification"
								: "");
						unpackToSibling(dest, optimized, canonical, isVerify, MonitorUtils.subMonitor(monitor, 20));
					}
				}
			}
			catch(CoreException e)
			{
				errors.add(Builder.getExceptionMessages(e));
				Buckminster.getLogger().error(e, e.getMessage());
			}
		}
		MonitorUtils.done(monitor);
	}

	private void mirror(List<InstallableUnit> iusToMirror, MetadataRepository source, final IMetadataRepository dest,
			IProgressMonitor monitor) throws CoreException
	{
		dest.addInstallableUnits(iusToMirror.toArray(new IInstallableUnit[iusToMirror.size()]));

		Builder builder = getBuilder();
		if(source != null && builder.isMirrorReferences())
		{
			Logger log = Buckminster.getLogger();
			for(RepositoryReference ref : source.getRepositoryReferences())
			{
				URI location = ref.getLocation();
				String refKey = location.toString();
				String refType = ref.getType() == IRepository.TYPE_METADATA
						? "meta-data"
						: "artifacts";
				if(!builder.isMatchedReference(refKey))
				{
					log.debug("- %s reference %s was ruled out by inclusion/exclusion patterns", refType, refKey);
					continue;
				}

				if(refKey.endsWith("/site.xml"))
					location = URI.create(refKey.substring(0, refKey.length() - 8));

				log.debug("- mirroring %s reference %s", refType, refKey);
				dest.addReference(location, ref.getNickname(), ref.getType(), 0);
			}
		}
	}
}