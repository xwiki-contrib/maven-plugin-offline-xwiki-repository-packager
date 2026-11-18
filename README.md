# Maven Off-line XWiki Repository Packager Plugin

Maven plugin to prepare packaged xwiki repositories for off-line installations

* Project Lead: [Denis Gervalle](http://www.xwiki.org/xwiki/bin/view/XWiki/dgervalle)
* Documentation: See below
* [Issue Tracker](https://jira.xwiki.org/browse/OXRPMP)
* Communication: [Mailing List](http://dev.xwiki.org/xwiki/bin/view/Community/MailingLists), [IRC](http://dev.xwiki.org/xwiki/bin/view/Community/IRC)
* [Development Practices](http://dev.xwiki.org)
* Minimal XWiki version supported: XWiki 9.0 (but you can package earlier version)
* License: LGPL 2.1
* Translations: N/A
* Sonar Dashboard: N/A
* Continuous Integration Status: N/A

## Usage

**Prerequisite**: [Install and setup Maven](http://dev.xwiki.org/xwiki/bin/view/Community/Building#HInstallingMaven) (Pay attention to setting up your `~/.m2/settings.xml` file as indicated).
 
To use this plugin to prepare an off-line repository for a new installation setup without Internet access, you need to
create a new Maven project with this simple pom:
    
    <?xml version="1.0" encoding="UTF-8"?>
    <project xmlns="http://maven.apache.org/POM/4.0.0" 
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
      <modelVersion>4.0.0</modelVersion>
      <groupId>my.group.id</groupId>
      <artifactId>my.artifact.id</artifactId>
      <version>1.0-SNAPSHOT</version>
      <name>My Offline Respository</name>
      <packaging>pom</packaging>
      <description>Copy all extensions used by My Project in the Extension Manager folder so that EM does not need to
        download them at runtime and My project can be installed offline.</description>
      <properties>
        <platform.version>${commons.version}</platform.version>
        <rendering.version>${commons.version}</rendering.version>
      </properties>
      <dependencies>
        <!-- XWiki WAR in order to have access to all the core modules -->
        <dependency>
          <groupId>org.xwiki.enterprise</groupId>
          <artifactId>xwiki-enterprise-web</artifactId>
          <version>${platform.version}</version>
          <type>war</type>
        </dependency>
        <!-- Main Wiki UI -->
        <dependency>
          <groupId>org.xwiki.enterprise</groupId>
          <artifactId>xwiki-enterprise-ui-mainwiki</artifactId>
          <version>${platform.version}</version>
          <type>xar</type>
        </dependency>
        <!-- Wiki UI -->
        <dependency>
          <groupId>org.xwiki.enterprise</groupId>
          <artifactId>xwiki-enterprise-ui-wiki</artifactId>
          <version>${platform.version}</version>
          <type>xar</type>
        </dependency>
        <!-- Admin Tools Application (usually useful) -->
        <dependency>
          <groupId>org.xwiki.contrib</groupId>
          <artifactId>xwiki-application-admintools</artifactId>
          <version>4.1.8</version>
          <type>xar</type>
        </dependency>
        <!-- More XWiki Extension you like to have -->
        <dependency>
            <groupId>an.extension.groupid</groupId>
            <artifactId>an.extension.artefactid</artifactId>
            <version>1.2.3</version>
            <type>jar_or_xar</type>
        </dependency>
        <!-- A special case, get the improved Blog Application between 8.4.2 and 9.2 -->
        <dependency>
          <groupId>org.xwiki.contrib.blog</groupId>
          <artifactId>application-blog-ui</artifactId>
          <version>9.3</version>
          <type>xar</type>
          <exclusions>
            <!-- Excludes the notification module introduced only in XWiki 9.2+ -->
            <exclusion>
              <groupId>org.xwiki.contrib.blog</groupId>
              <artifactId>application-blog-notification</artifactId>
            </exclusion>
          </exclusions>    
        </dependency>
      </dependencies>
      <build>
        <plugins>
          <!-- To prepare the repository -->
          <plugin>
            <groupId>org.xwiki.contrib</groupId>
            <artifactId>offline-xwiki-repository-packager-maven-plugin</artifactId>
            <version>1.0</version>
            <configuration>
              <excludes>
                <!-- Exclude JARs that have legacy versions in the WAR -->
                <exclude>org.xwiki.commons:xwiki-commons-component-api</exclude>
                <exclude>org.xwiki.commons:xwiki-commons-component-default</exclude>
                <exclude>org.xwiki.rendering:xwiki-rendering-api</exclude>
                <exclude>org.xwiki.platform:xwiki-platform-office-importer</exclude>
                <exclude>org.xwiki.platform:xwiki-platform-oldcore</exclude>
                <exclude>org.xwiki.platform:xwiki-platform-rendering-macro-include</exclude>
                <!-- A special case, to the improved Blog Application between 8.4.2 and 9.2 -->
                <exclude>org.xwiki.platform:xwiki-platform-blog-ui</exclude>
              </excludes>
            </configuration>
            <executions>
              <execution>
                <phase>compile</phase>
                <goals>
                  <goal>package-extensions</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
          <!-- To assemble the repository in a ZIP file for easy transfer -->
          <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-assembly-plugin</artifactId>
            <configuration>
              <appendAssemblyId>false</appendAssemblyId>
              <descriptors>
                <descriptor>${basedir}/src/assemble/offline-repository.xml</descriptor>
              </descriptors>
            </configuration>
            <executions>
              <execution>
                <phase>package</phase>
                <goals>
                  <goal>single</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>

      <!-- Not available on Maven central -->
      <pluginRepositories>
        <pluginRepository>
          <id>xwiki-plugin-releases</id>
          <name>XWiki Plugins Repository</name>
          <url>http://nexus.xwiki.org/nexus/content/groups/public/</url>
          <layout>default</layout>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
          <releases>
            <enabled>true</enabled>
          </releases>
        </pluginRepository>
      </pluginRepositories>
      <repositories>
        <repository>
          <releases>
            <enabled>true</enabled>
          </releases>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
          <id>xwiki-releases</id>
          <name>XWiki Repository</name>
          <url>http://nexus.xwiki.org/nexus/content/groups/public/</url>
        </repository>
      </repositories>
    </project>
    
And an assembly definition into src/assemble/offline-repository.xml with the following content:

    <assembly>
      <id>offline-repository</id>
      <formats>
        <format>zip</format>
      </formats>
      <includeBaseDirectory>false</includeBaseDirectory>
      <fileSets>
        <fileSet>
          <directory>${project.build.directory}/data/</directory>
          <outputDirectory></outputDirectory>
        </fileSet>
      </fileSets>
    </assembly>

You can build your project with `mvn package`, and you will get your repository packaged in the `target` folder, under
the `data` folder, and a ready to transfer zip file in `my.artifact.id-version.zip`.

To use that repository, in a fresh installation of XWiki (no database, nor existing extensions installed yet), unzip the
resulting package in the permanent directory. Be careful to give appropriate rights to allow the servlet container to
write over the extracted files.
