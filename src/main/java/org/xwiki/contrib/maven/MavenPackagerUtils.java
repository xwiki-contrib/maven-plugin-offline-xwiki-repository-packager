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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Developer;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.extension.DefaultExtensionAuthor;
import org.xwiki.extension.DefaultExtensionDependency;
import org.xwiki.extension.Extension;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionLicense;
import org.xwiki.extension.ExtensionLicenseManager;
import org.xwiki.extension.repository.internal.local.DefaultLocalExtension;
import org.xwiki.extension.version.internal.DefaultVersionConstraint;
import org.xwiki.properties.ConverterManager;

/**
 * Utility methods for packager mojos.
 * TODO: most of this code comes form xwiki-platform-tool-packager-plugin. We should have it in commons.
 *
 * @version $Id$
 */
public class MavenPackagerUtils
{
    private final MavenSession session;
    private final ProjectBuilder projectBuilder;
    private final ExtensionLicenseManager licenseManager;
    private final ConverterManager converter;

    public MavenPackagerUtils(MavenSession session, ProjectBuilder projectBuilder,
        ComponentManager xwikiComponentManager) throws MojoExecutionException
    {
        this.session = session;
        this.projectBuilder = projectBuilder;
        try {
            this.licenseManager = xwikiComponentManager.getInstance(ExtensionLicenseManager.class);
            this.converter = xwikiComponentManager.getInstance(ConverterManager.class);
        } catch (ComponentLookupException e) {
            throw new MojoExecutionException("Failed to initialize packager", e);
        }
    }

    /**
     * Retrieve the value of a property in a given maven model.
     *
     * @param model the model
     * @param propertyName the property name
     * @return the property value
     */
    private String getProperty(Model model, String propertyName)
    {
        return model.getProperties().getProperty(PackageExtensionsMojo.MPKEYPREFIX + propertyName);
    }

    /**
     * Retrieve the value of a property in a given maven model, return a default value if none found.
     *
     * @param model the model
     * @param propertyName the property name
     * @param def the default value
     * @return the property value
     */
    private String getPropertyString(Model model, String propertyName, String def)
    {
        return StringUtils.defaultString(getProperty(model, propertyName), def);
    }

    /**
     * Build or retrieve an extension license based on Maven license information.
     *
     * @param license the maven license information
     * @return an extension license
     */
    private ExtensionLicense getExtensionLicense(License license)
    {
        if (license.getName() == null) {
            return new ExtensionLicense("noname", null);
        }

        return createLicenseByName(license.getName());
    }

    /**
     * Build or retrieve an extension license based on a license name.
     *
     * @param name the license name
     * @return an extension license
     */
    private ExtensionLicense createLicenseByName(String name)
    {
        ExtensionLicense extensionLicense = licenseManager.getLicense(name);

        return extensionLicense != null ? extensionLicense : new ExtensionLicense(name, null);
    }

    /**
     * Build without processing any plugin the maven project of an artifact, and return it.
     *
     * @param artifact the artifact
     * @return the maven project of this artifact
     * @throws MojoExecutionException if the build fails
     */
    public MavenProject getMavenProject(Artifact artifact) throws MojoExecutionException
    {
        if (artifact == null) {
            return null;
        }

        try {
            ProjectBuildingRequest request =
                new DefaultProjectBuildingRequest(this.session.getProjectBuildingRequest())
                    // We don't want to execute any plugin here
                    .setProcessPlugins(false)
                    // It's not this plugin job to validate this pom.xml
                    .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                    // Use the repositories configured for the built project instead of the default Maven ones
                    .setRemoteRepositories(this.session.getCurrentProject().getRemoteArtifactRepositories());
            // Note: build() will automatically get the POM artifact corresponding to the passed artifact.
            ProjectBuildingResult result = this.projectBuilder.build(artifact, request);
            return result.getProject();
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException(String.format("Failed to build project for [%s]", artifact), e);
        }
    }

    public Extension toExtension(Artifact artifact, Collection<Exclusion> exclusions) throws MojoExecutionException
    {
        MavenProject project = getMavenProject(artifact);
        Model model = project.getModel();

        DefaultLocalExtension extension = new DefaultLocalExtension(null,
            new ExtensionId(artifact.getGroupId() + ':' + artifact.getArtifactId(), artifact.getBaseVersion()),
            artifact.getType());

        extension.setName(getPropertyString(model, PackageExtensionsMojo.MPNAME_NAME, model.getName()));
        extension.setSummary(getPropertyString(model, PackageExtensionsMojo.MPNAME_SUMMARY, model.getDescription()));
        extension.setWebsite(getPropertyString(model, PackageExtensionsMojo.MPNAME_WEBSITE, model.getUrl()));

        // authors
        for (Developer developer : model.getDevelopers()) {
            URL authorURL = null;
            if (developer.getUrl() != null) {
                try {
                    authorURL = new URL(developer.getUrl());
                } catch (MalformedURLException e) {
                    // TODO: log ?
                }
            }

            extension.addAuthor(new DefaultExtensionAuthor(StringUtils.defaultIfBlank(developer.getName(),
                developer.getId()), authorURL));
        }

        // licenses
        if (!model.getLicenses().isEmpty()) {
            for (License license : model.getLicenses()) {
                extension.addLicense(getExtensionLicense(license));
            }
        }

        // features
        String featuresString = getProperty(model, PackageExtensionsMojo.MPNAME_FEATURES);
        if (StringUtils.isNotBlank(featuresString)) {
            featuresString = featuresString.replaceAll("[\r\n]", "");
            extension.setFeatures(converter.<Collection<String>>convert(List.class, featuresString));
        }

        // dependencies
        for (Dependency mavenDependency : model.getDependencies()) {
            if (!mavenDependency.isOptional()
                && (mavenDependency.getScope().equals("compile") || mavenDependency.getScope().equals("runtime"))) {
                boolean excluded = false;
                for (Exclusion exclusion : exclusions) {
                    if (mavenDependency.getArtifactId().equals(exclusion.getArtifactId())
                        && mavenDependency.getGroupId().equals(exclusion.getGroupId())) {
                        excluded = true;
                        break;
                    }
                }
                if (!excluded) {
                    extension.addDependency(new DefaultExtensionDependency(mavenDependency.getGroupId() + ':'
                        + mavenDependency.getArtifactId(), new DefaultVersionConstraint(mavenDependency.getVersion())));
                }
            }
        }

        extension.setFile(artifact.getFile());
        return extension;
    }
}
