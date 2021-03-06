/*****************************************************************************
 * (c) 2004-2006
 * Thomas Hallgren, Kenneth Olwing, Mitch Sonies
 * Pontus Rydin, Nils Unden, Peer Torngren
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the individual
 * copyright holders listed above, as Initial Contributors under such license.
 * The text of such license is available at www.eclipse.org.
 ****************************************************************************/

package org.eclipse.buckminster.ant.taskdefs;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Chmod;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.PropertySet;

/**
 * This task will only care about the property values of the properties found in
 * the given property sets. Each property value is assumed to be in the form
 * &lt;include pattern&gt;:&lt;permission&gt; The <code>pattern</code> is used
 * when creating a fileset that is then passed to a normal chmod task with the
 * <code>permission</code>.
 * 
 * @author Thomas Hallgren
 */
public class MultiChmod extends Task {
	private static final Pattern fileAndPerm = Pattern.compile("^(.+):(\\d+)$");

	private final ArrayList<PropertySet> propertySets = new ArrayList<PropertySet>();

	private File dir;

	public void addPropertySet(PropertySet propertySet) {
		propertySets.add(propertySet);
	}

	@Override
	public void execute() throws BuildException {
		Map<String, FileSet> entries = new HashMap<String, FileSet>();
		for (PropertySet propertySet : propertySets) {
			Properties props = propertySet.getProperties();
			Enumeration<?> propNames = props.propertyNames();
			while (propNames.hasMoreElements())
				addAllEntries(entries, props.getProperty((String) propNames.nextElement()));
		}
		Project p = getProject();
		for (Map.Entry<String, FileSet> entry : entries.entrySet()) {
			Chmod chmod = new Chmod();
			chmod.setProject(p);
			chmod.setPerm(entry.getKey());
			chmod.addFileset(entry.getValue());
			chmod.perform();
			log("Changing to mode " + entry.getKey() + " for files " + entry.getValue());
		}
	}

	public void setDir(File dir) {
		this.dir = dir;
	}

	private void addAllEntries(Map<String, FileSet> entries, String propVal) throws BuildException {
		if (propVal == null || propVal.length() == 0)
			return;

		StringTokenizer tokens = new StringTokenizer(propVal, ",");
		while (tokens.hasMoreTokens())
			addEntry(entries, tokens.nextToken().trim());
	}

	private void addEntry(Map<String, FileSet> entries, String propVal) throws BuildException {
		Matcher m = fileAndPerm.matcher(propVal);
		if (!m.matches())
			throw new BuildException("Illegal property value: " + propVal, getLocation());

		Project p = getProject();
		String include = m.group(1);
		String perm = m.group(2);
		FileSet fs = entries.get(perm);
		if (fs == null) {
			fs = new FileSet();
			fs.setProject(p);
			fs.setDir(dir);
			entries.put(perm, fs);
		}
		fs.createInclude().setName(p.replaceProperties(include));
	}
}
