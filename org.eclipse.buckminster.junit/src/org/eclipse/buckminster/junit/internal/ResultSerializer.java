/*******************************************************************************
 * Copyright (c) 2009, eXXcellent solutions gmbh and others
 * The code, documentation and other materials contained herein have been
 * licensed under the Eclipse Public License - v 1.0 by the copyright holder
 * listed above, as the Initial Contributor under such license. The text of
 * such license is available at www.eclipse.org.
 *
 * Contributors:
 *     Achim Demelt - initial API and implementation
 *     Matthias Kappeller - Bug 321064 - No JUnit TestReport created for huge report files
 *******************************************************************************/
package org.eclipse.buckminster.junit.internal;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.jdt.junit.model.ITestSuiteElement;
import org.eclipse.jdt.junit.model.ITestElement.FailureTrace;
import org.eclipse.jdt.junit.model.ITestElement.Result;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Serializes a given {@link ITestRunSession} into an ant-junit-like XML file.
 * Most of the code is copied from JDT JUnit's TestRunSessionSerializer class,
 * but it only uses public API and tries to conform to the (unspecified)
 * ant-junit format.
 */
public class ResultSerializer implements XMLReader {
	private static final String EMPTY = ""; //$NON-NLS-1$

	private static final String CDATA = "CDATA"; //$NON-NLS-1$

	private static final NumberFormat timeFormat = new DecimalFormat("0.0##", new DecimalFormatSymbols(Locale.US)); //$NON-NLS-1$ // not localized, parseable by Double.parseDouble(..)

	private static final Attributes NO_ATTS = new AttributesImpl();

	private ITestRunSession testRunSession;

	private ContentHandler contentHandler;

	private ErrorHandler errorHandler;

	private TestListener testListener;

	private IStreamMonitor[] stdOut;

	private IStreamMonitor[] stdErr;

	private boolean terseXML;

	private boolean flatXML;

	private String suiteStack = ""; //$NON-NLS-1$

	public ResultSerializer(TestListener listener, IStreamMonitor[] stdout, IStreamMonitor[] stderr, boolean terseXML, boolean flatXML) {
		if (listener.getTestRunSession() == null) {
			throw new IllegalArgumentException(Messages.ResultSerializer_No_Test_Session);
		}

		this.testListener = listener;
		this.testRunSession = listener.getTestRunSession();
		this.stdOut = stdout;
		this.stdErr = stderr;
		this.terseXML = terseXML;
		this.flatXML = flatXML;
	}

	@Override
	public ContentHandler getContentHandler() {
		return contentHandler;
	}

	@Override
	public DTDHandler getDTDHandler() {
		return null;
	}

	@Override
	public EntityResolver getEntityResolver() {
		return null;
	}

	@Override
	public ErrorHandler getErrorHandler() {
		return errorHandler;
	}

	@Override
	public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
		return false;
	}

	@Override
	public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
		return null;
	}

	@Override
	public void parse(InputSource input) throws IOException, SAXException {
		if (contentHandler == null)
			throw new SAXException("ContentHandler missing"); //$NON-NLS-1$

		contentHandler.startDocument();
		handleTestRun();
		contentHandler.endDocument();
	}

	@Override
	public void parse(String systemId) throws IOException, SAXException {
		// ignore
	}

	@Override
	public void setContentHandler(ContentHandler handler) {
		this.contentHandler = handler;
	}

	@Override
	public void setDTDHandler(DTDHandler handler) {
		// ignore
	}

	@Override
	public void setEntityResolver(EntityResolver resolver) {
		// ignore
	}

	@Override
	public void setErrorHandler(ErrorHandler handler) {
		this.errorHandler = handler;
	}

	@Override
	public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
		// ignore
	}

	@Override
	public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
		// ignore
	}

	private void addCDATA(AttributesImpl atts, String name, String value) {
		atts.addAttribute(EMPTY, EMPTY, name, CDATA, value);
	}

	private void addFailure(ITestElement testElement) throws SAXException {
		FailureTrace failureTrace = testElement.getFailureTrace();
		if (failureTrace != null) {
			AttributesImpl failureAtts = new AttributesImpl();
			String failureKind = testElement.getTestResult(false) == Result.ERROR ? IXMLTags.NODE_ERROR : IXMLTags.NODE_FAILURE;
			startElement(failureKind, failureAtts);
			String expected = failureTrace.getExpected();
			String actual = failureTrace.getActual();
			if (expected != null) {
				startElement(IXMLTags.NODE_EXPECTED, NO_ATTS);
				contentHandler.characters(expected.toCharArray(), 0, expected.length());
				endElement(IXMLTags.NODE_EXPECTED);
			}
			if (actual != null) {
				startElement(IXMLTags.NODE_ACTUAL, NO_ATTS);
				contentHandler.characters(actual.toCharArray(), 0, actual.length());
				endElement(IXMLTags.NODE_ACTUAL);
			}
			String trace = failureTrace.getTrace();
			contentHandler.characters(trace.toCharArray(), 0, trace.length());
			endElement(failureKind);
		}
	}

	private void endElement(String name) throws SAXException {
		contentHandler.endElement(EMPTY, name, name);
	}

	private void handleTestElement(ITestElement testElement) throws SAXException {
		if (testElement instanceof ITestSuiteElement) {
			ITestSuiteElement testSuiteElement = (ITestSuiteElement) testElement;

			if (flatXML) {
				suiteStack = suiteStack + testSuiteElement.getSuiteTypeName() + "$"; //$NON-NLS-1$
			} else {
				AttributesImpl atts = new AttributesImpl();
				addCDATA(atts, IXMLTags.ATTR_NAME, testSuiteElement.getSuiteTypeName());
				if (!Double.isNaN(testSuiteElement.getElapsedTimeInSeconds()))
					addCDATA(atts, IXMLTags.ATTR_TIME, timeFormat.format(testSuiteElement.getElapsedTimeInSeconds()));

				startElement(IXMLTags.NODE_TESTSUITE, atts);
				addFailure(testElement);
			}

			ITestElement[] children = testSuiteElement.getChildren();
			for (int i = 0; i < children.length; i++) {
				handleTestElement(children[i]);
			}

			if (flatXML) {
				suiteStack = suiteStack.substring(0, suiteStack.lastIndexOf(testSuiteElement.getSuiteTypeName()));
			} else {
				endElement(IXMLTags.NODE_TESTSUITE);
			}
		} else if (testElement instanceof ITestCaseElement) {
			ITestCaseElement testCaseElement = (ITestCaseElement) testElement;

			AttributesImpl atts = new AttributesImpl();
			String testClassName = testCaseElement.getTestClassName();
			if (flatXML) {
				if (suiteStack.endsWith(testClassName + "$")) //$NON-NLS-1$)
					testClassName = suiteStack.substring(0, suiteStack.length() - 1);
				else
					testClassName = suiteStack + testClassName;
			}
			addCDATA(atts, IXMLTags.ATTR_NAME, testCaseElement.getTestMethodName());
			addCDATA(atts, IXMLTags.ATTR_CLASSNAME, testClassName);
			if (!Double.isNaN(testCaseElement.getElapsedTimeInSeconds()))
				addCDATA(atts, IXMLTags.ATTR_TIME, timeFormat.format(testCaseElement.getElapsedTimeInSeconds()));
			if (testCaseElement.getTestResult(false) == ITestElement.Result.IGNORED)
				addCDATA(atts, IXMLTags.ATTR_IGNORED, Boolean.TRUE.toString());

			startElement(IXMLTags.NODE_TESTCASE, atts);
			addFailure(testElement);
			endElement(IXMLTags.NODE_TESTCASE);
		} else {
			throw new IllegalStateException(String.valueOf(testElement));
		}
	}

	private void handleTestRun() throws SAXException {
		AttributesImpl atts = new AttributesImpl();
		addCDATA(atts, IXMLTags.ATTR_NAME, testRunSession.getTestRunName());
		addCDATA(atts, IXMLTags.ATTR_TESTS, String.valueOf(testListener.getOverallCount()));
		addCDATA(atts, IXMLTags.ATTR_ERRORS, String.valueOf(testListener.getErrorCount()));
		addCDATA(atts, IXMLTags.ATTR_FAILURES, String.valueOf(testListener.getFailureCount()));
		addCDATA(atts, IXMLTags.ATTR_IGNORED, String.valueOf(testListener.getIgnoreCount()));
		if (!Double.isNaN(testRunSession.getElapsedTimeInSeconds()))
			addCDATA(atts, IXMLTags.ATTR_TIME, timeFormat.format(testRunSession.getElapsedTimeInSeconds()));

		startElement(flatXML ? IXMLTags.NODE_TESTSUITE : IXMLTags.NODE_TESTSUITES, atts);

		for (ITestElement element : testRunSession.getChildren())
			handleTestElement(element);

		if (!terseXML) {
			writeStdOut();
			writeStdErr();
		}

		endElement(flatXML ? IXMLTags.NODE_TESTSUITE : IXMLTags.NODE_TESTSUITES);
	}

	private void startElement(String name, Attributes atts) throws SAXException {
		contentHandler.startElement(EMPTY, name, name, atts);
	}

	private void writeStdErr() throws SAXException {
		for (IStreamMonitor sm : stdErr) {
			if (sm == null)
				continue;
			String contents = sm.getContents();
			if (contents.length() > 0) {
				startElement(IXMLTags.NODE_SYSTEM_ERR, NO_ATTS);
				contentHandler.characters(contents.toCharArray(), 0, contents.length());
				endElement(IXMLTags.NODE_SYSTEM_ERR);
			}
		}
	}

	private void writeStdOut() throws SAXException {
		for (IStreamMonitor sm : stdOut) {
			if (sm == null)
				continue;
			String contents = sm.getContents();
			if (contents.length() > 0) {
				startElement(IXMLTags.NODE_SYSTEM_OUT, NO_ATTS);
				contentHandler.characters(contents.toCharArray(), 0, contents.length());
				endElement(IXMLTags.NODE_SYSTEM_OUT);
			}
		}
	}
}
