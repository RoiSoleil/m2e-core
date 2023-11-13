/*******************************************************************************
 * Copyright (c) 2020, 2023 Red Hat, Inc. and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.m2e.jdt.tests;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Predicate;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.preferences.MavenConfigurationImpl;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;
import org.junit.*;

@SuppressWarnings("restriction")
public class JavaConfigurationTest extends AbstractMavenProjectTestCase {

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		((MavenConfigurationImpl) MavenPlugin.getMavenConfiguration()).setAutomaticallyUpdateConfiguration(true);
		setAutoBuilding(true);
	}

	@Test
	public void testFileChangeUpdatesJDTSettings() throws CoreException, IOException, InterruptedException {
		IJavaProject project = importResourceProject("/projects/compilerSettings/pom.xml");
		assertEquals("1.8", project.getOption(JavaCore.COMPILER_SOURCE, false));
		IFile pomFileWS = project.getProject().getFile("pom.xml");
		String pomContent = Files.readString(Path.of(pomFileWS.getLocationURI()));
		pomContent = pomContent.replace("1.8", "11");
		pomFileWS.setContents(new ByteArrayInputStream(pomContent.getBytes()), true, false, null);
		waitForJobsToComplete();
		assertEquals("11", project.getOption(JavaCore.COMPILER_SOURCE, false));
	}

	@Test
	public void testComplianceVsReleaseSettings() throws CoreException, IOException, InterruptedException {
		IJavaProject project = importResourceProject("/projects/compilerReleaseSettings/pom.xml");
		assertEquals("1.8", project.getOption(JavaCore.COMPILER_SOURCE, false));
		assertEquals("1.8", project.getOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, false));
		assertEquals("1.8", project.getOption(JavaCore.COMPILER_COMPLIANCE, false));
		assertEquals(JavaCore.ENABLED, project.getOption(JavaCore.COMPILER_RELEASE, false));
		IFile pomFileWS = project.getProject().getFile("pom.xml");
		String pomContent = Files.readString(Path.of(pomFileWS.getLocationURI()));
		pomContent = pomContent.replace(">8<", ">11<");
		pomFileWS.setContents(new ByteArrayInputStream(pomContent.getBytes()), true, false, null);
		waitForJobsToComplete();
		assertEquals("11", project.getOption(JavaCore.COMPILER_SOURCE, false));
		assertEquals("11", project.getOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, false));
		assertEquals("11", project.getOption(JavaCore.COMPILER_COMPLIANCE, false));
		assertEquals(JavaCore.ENABLED, project.getOption(JavaCore.COMPILER_RELEASE, false));
	}

	@Test
	public void testJDTWarnings() throws CoreException, IOException, InterruptedException {
		IJavaProject project = importResourceProject("/projects/compilerWarnings/pom.xml");
		IFile file = project.getProject().getFile("src/main/java/A.java");
		project.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		waitForJobsToComplete();
		IMarker[] findMarkers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
		assertArrayEquals(new IMarker[0], findMarkers);
	}

	@Test
	public void testSkipAllTest() throws CoreException, IOException, InterruptedException {
		IJavaProject project = importResourceProject("/projects/skipAllTest/pom.xml");
		assertTrue(Arrays.stream(project.getRawClasspath()).noneMatch(IClasspathEntry::isTest));
	}

	@Test
	public void testSkipTestCompilation() throws CoreException, IOException, InterruptedException {
		IJavaProject project = importResourceProject("/projects/skipTestCompilation/pom.xml");
		assertEquals(0, classpathEntriesCount(project, TEST_SOURCES));
		assertEquals(1, classpathEntriesCount(project, TEST_RESOURCES));
	}

	@Test
	public void testSkipTestResources() throws CoreException, IOException, InterruptedException {
		IJavaProject project = importResourceProject("/projects/skipTestResources/pom.xml");
		assertEquals(1, classpathEntriesCount(project, TEST_SOURCES));
		assertEquals(0, classpathEntriesCount(project, TEST_RESOURCES));
	}

	@Test
	public void testSkipOnlyOneOfMultipleExecutions() throws CoreException, IOException, InterruptedException {
		IJavaProject project = importResourceProject("/projects/skipOnlyOneOfMultipleExecutions/pom.xml");
		assertEquals(1, classpathEntriesCount(project, TEST_SOURCES));
		assertEquals(1, classpathEntriesCount(project, TEST_RESOURCES));
	}

	@Test
	public void testSkipNone() throws CoreException, IOException, InterruptedException {
		IJavaProject project = importResourceProject("/projects/skipNone/pom.xml");
		assertEquals(1, classpathEntriesCount(project, TEST_SOURCES));
		assertEquals(1, classpathEntriesCount(project, TEST_RESOURCES));
	}

	
	@Test
	public void testComplianceVsEnablePreviewSettings() throws CoreException, IOException, InterruptedException {
		IJavaProject project = importResourceProject("/projects/compilerEnablePreviewSettings/pom.xml");
		assertEquals("11", project.getOption(JavaCore.COMPILER_COMPLIANCE, false));
		assertEquals(JavaCore.ENABLED, project.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		assertEquals(JavaCore.IGNORE, project.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
	}

	@Test
	public void testAddSourceResource() throws CoreException, IOException, InterruptedException {
		IJavaProject project = importResourceProject("/projects/add-source-resource/submoduleA/pom.xml");
		assertEquals(4, project.getRawClasspath().length);
	}
	// --- utility methods ---

	private static final Predicate<IClasspathEntry> TEST_SOURCES = cp -> cp.isTest()
			&& cp.getPath().toString().contains("test/java");
	private static final Predicate<IClasspathEntry> TEST_RESOURCES = cp -> cp.isTest()
			&& cp.getPath().toString().contains("test/resources");

	private long classpathEntriesCount(IJavaProject project, Predicate<IClasspathEntry> testSources)
			throws JavaModelException {
		return Arrays.stream(project.getRawClasspath()).filter(testSources).count();
	}

	private IJavaProject importResourceProject(String name) throws IOException, InterruptedException, CoreException {
		File pomFile = new File(FileLocator.toFileURL(JavaConfigurationTest.class.getResource(name)).getFile());
		waitForJobsToComplete();
		IProject project = importProject(pomFile.getAbsolutePath());
		waitForJobsToComplete();
		return JavaCore.create(project);
	}
}
