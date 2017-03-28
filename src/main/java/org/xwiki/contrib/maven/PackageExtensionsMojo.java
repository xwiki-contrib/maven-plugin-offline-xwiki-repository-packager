/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.extension.repository.LocalExtensionRepository;
import org.xwiki.extension.repository.LocalExtensionRepositoryException;

/**
 * @version $Id: $
 */
@Mojo(name = "package-extensions", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class PackageExtensionsMojo extends AbstractMojo
{
    private static final String XAR_TYPE = "xar";

    private static final String JAR_TYPE = "jar";

    private static final String JAR_DIRECTORY = "WEB-INF/lib/";

    public static final String MPKEYPREFIX = "xwiki.extension.";

    public static final String MPNAME_NAME = "name";

    public static final String MPNAME_SUMMARY = "summary";

    public static final String MPNAME_WEBSITE = "website";

    public static final String MPNAME_FEATURES = "features";

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${project.build.directory}/data/", readonly = true)
    private File xwikiDataDir;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Parameter
    private String[] excludes;

    @Component(role = ProjectBuilder.class)
    private ProjectBuilder projectBuilder;

    private ComponentManager xwikiComponentManager = org.xwiki.environment.System.initialize();

    private Collection<String> getJarsIncludedInWar(Artifact web) throws IOException, MojoExecutionException
    {
        if (web == null) {
            return Collections.emptyList();
        }

        getLog().info(String.format("Excluding Base WAR [%s:%s].", web.getGroupId(),
            web.getArtifactId()));

        Collection<String> jars = new ArrayList<>();

        // TODO: replace this by a a method which look to the POM of the WAR
        // Open the war and list all the jars
        ZipFile zipFile = new ZipFile(web.getFile());
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            String entryName = entries.nextElement().getName();
            if (entryName.startsWith(JAR_DIRECTORY) && entryName.endsWith(".jar")) {
                jars.add(entryName.substring(JAR_DIRECTORY.length()));
            }
        }
        zipFile.close();

        return jars;
    }

    private Artifact getWarArtifact()
    {
        Artifact web = null;

        List<Dependency> deps = mavenProject.getModel().getDependencies();
        for (Dependency dep : deps) {
            if (dep.getType().equals("war")) {
                web = mavenProject.getArtifactMap().get(dep.getGroupId() + ':' + dep.getArtifactId());
                break;
            }
        }
        return web;
    }

    private boolean isJarForbidden(Artifact artifact, Collection<String> jarsIncludedInWar)
    {
        for (String jar : jarsIncludedInWar) {
            if (jar.startsWith(artifact.getArtifactId())) {
                return true;
            }
        }
        return false;
    }

    private boolean includeArtifacts(Artifact artifact, Collection<String> jarsInWar)
    {
        if (!JAR_TYPE.equals(artifact.getType()) && !XAR_TYPE.equals(artifact.getType())) {
            return false;
        }

        if (!"runtime".equals(artifact.getScope()) && !"compile".equals(artifact.getScope())) {
            return false;
        }

        if (isJarForbidden(artifact, jarsInWar)) {
            return false;
        }

        String id = String.format("%s:%s", artifact.getGroupId(), artifact.getArtifactId());
        for (String exclude : excludes) {
            if (exclude.equals(id)) {
                return false;
            }
        }

        return true;
    }

    private List<Exclusion> getExclusions(Artifact artifact)
    {
        for (Dependency dep : mavenProject.getDependencies()) {
            if (dep.getArtifactId().equals(artifact.getArtifactId()) && dep.getGroupId().equals(artifact.getGroupId())) {
                return dep.getExclusions();
            }
        }

        return Collections.emptyList();
    }

    @Override
    public void execute() throws MojoExecutionException
    {
        System.setProperty("xwiki.data.dir", this.xwikiDataDir.getAbsolutePath());

        try {
            LocalExtensionRepository localExtensionRepository =
                xwikiComponentManager.getInstance(LocalExtensionRepository.class);

            // Identify artifacts that are already bundled in the WAR
            Collection<String> jarsInWar = getJarsIncludedInWar(getWarArtifact());

            MavenPackagerUtils mavenutils = new MavenPackagerUtils(session, projectBuilder, xwikiComponentManager);

            // Put all dependencies in the local repository (in /data/extensions)
            for (Artifact artifact : mavenProject.getArtifacts()) {
                if (includeArtifacts(artifact, jarsInWar)) {
                    getLog().info(String.format("Copying dependency [%s:%s, %s].", artifact.getGroupId(),
                        artifact.getArtifactId(), artifact.getVersion()));
                    localExtensionRepository.storeExtension(mavenutils.toExtension(artifact, getExclusions(artifact)));
                }
            }
        } catch (ComponentLookupException | LocalExtensionRepositoryException | IOException e) {
            e.printStackTrace();
        }
    }
}
