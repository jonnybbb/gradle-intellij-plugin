package org.gradle.plugins.idea

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSet
import groovy.util.slurpersupport.GPathResult
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskAction
import org.gradle.api.plugins.JavaPlugin

public class IdeaModule extends DefaultTask {
    File imlDir
    File moduleDir
    File outputFile
    def sourceDirs
    def testSourceDirs
    File outputDir
    File testOutputDir
    Map scopes = [:]

    @TaskAction
    void updateXML() {
        GPathResult xmlRoot = getSourceRoot(getOutputFile());

        def moduleRootManager = xmlRoot.component.find { it.@name == 'NewModuleRootManager' }
        moduleRootManager.replaceNode {
            component(name: 'NewModuleRootManager', 'inherit-compiler-output': 'false') {
                content(url: toModuleURL(moduleDir)) {
                    sourceDirs.each { File file ->
                        sourceFolder(url: toModuleURL(file), isTestSource: 'false')
                    }
                    testSourceDirs.each { File file ->
                        sourceFolder(url: toModuleURL(file), isTestSource: 'true')
                    }
                }
                if (outputDir) output(url: toModuleURL(outputDir))
                if (testOutputDir) 'output-test'(url: toModuleURL(testOutputDir))

                orderEntry(type: 'inheritedJdk')
                orderEntry(type: 'sourceFolder', forTests: 'false')

                scopes.each {scope, configuration ->
                    def libs = getExternalDependencies(scope)
                    libs.each { lib ->
                        orderEntry(type: 'module-library', scope: "${scope.toUpperCase()}", exported: '') {
                            library {
                                CLASSES() { root(url: toModuleURL(lib)) }
                                JAVADOC()
                                SOURCES()
                            }
                        }
                    }
                    def projectDependencies = getProjectDependencies(scope)
                    projectDependencies.each { project ->
                        orderEntry(type: 'module', scope: "${scope.toUpperCase()}", 'module-name': project.name, exported: '')
                    }
                }

                orderEntryProperties()
            }
        }
        Util.prettyPrintXML(getOutputFile(), xmlRoot);
    }


    def getProjectDependencies(String scope) {
        if (scopes[scope]) {
            configurations = scopes[scope]
            def included = configurations.plus.inject([] as Set) { includes, configuration ->
                includes + configuration.getAllDependencies(ProjectDependency).collect { projectDependency -> projectDependency.dependencyProject }
            }
            println('IIIIIIIIII ' + scope + ' ' + included)
            configurations.minus.each { configuration ->
                included = included - configuration.getAllDependencies(ProjectDependency).collect { projectDependency -> projectDependency.dependencyProject }
            }
            return included
        }
        return []
    }

    def getExternalDependencies(String scope) {
        if (scopes[scope]) {
            configurations = scopes[scope]
            def included = configurations.plus.inject([] as Set) { includes, configuration ->
                includes + configuration.files {
                    !(it instanceof ProjectDependency)
                }
            }
            def excluded = configurations.minus.inject([] as Set) { excludes, configuration ->
                excludes + configuration.files {
                    !(it instanceof ProjectDependency)
                }
            }
            return included - excluded
        }
        return []
    }

    String toModuleURL(File file) {
        Util.getRelativeURI(imlDir, '$MODULE_DIR$', file)
    }

    def String getDefaultXML() {
        '''<module relativePaths="true" type="JAVA_MODULE" version="4">
        <component name="NewModuleRootManager"/>
        <component name="FacetManager"/>
      </module>
      '''
    }

    private GPathResult getSourceRoot(File outputFile) {
        XmlSlurper slurper = new XmlSlurper();
        if (outputFile.exists()) {
            try {
                return slurper.parse(outputFile);
            }
            catch (Exception exception) {
                System.out.println("Error opening existing file, pretending file does not exist");
            }
        }
        return slurper.parseText(defaultXML);
    }

}