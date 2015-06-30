package com.versioneye;

import com.versioneye.dto.ProjectJsonResponse;
import com.versioneye.utils.DependencyUtils;
import com.versioneye.utils.JsonUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Methods required to deal with projects resource
 */
public class ProjectMojo extends SuperMojo {

    protected ByteArrayOutputStream getDirectDependenciesJsonStream(String nameStrategy) throws Exception {
        List<Plugin> plugins = new ArrayList<Plugin>();
        if (trackPlugins){
            plugins = getPluginsFromXml();
        }
        List<Dependency> dependencies = project.getDependencies();
        if (project.getDependencyManagement() != null &&
                project.getDependencyManagement().getDependencies() != null &&
                project.getDependencyManagement().getDependencies().size() > 0){
            dependencies.addAll(project.getDependencyManagement().getDependencies());
        }
        List<Dependency> filteredDependencies = filterForScopes(dependencies);
        JsonUtils jsonUtils = new JsonUtils();
        return jsonUtils.dependenciesToJson(project, filteredDependencies, plugins, nameStrategy);
    }

    protected Map<String, Object> getDirectDependenciesJsonMap(String nameStrategy) throws Exception {
        List<Dependency> dependencies = project.getDependencies();
        if (dependencies == null || dependencies.isEmpty()){
            return null;
        } else {
            iterateThrough(dependencies);
        }
        JsonUtils jsonUtils = new JsonUtils();
        List<Map<String, Object>> dependencyHashes = jsonUtils.getDependencyHashes(dependencies, project.getPluginManagement().getPlugins());
        return jsonUtils.getJsonPom(project, dependencyHashes, nameStrategy);
    }

    protected ByteArrayOutputStream getDirectArtifactsJsonStream() throws Exception {
        DependencyNode root = getDependencyNode(new PreorderNodeListGenerator());
        List<Artifact> directDependencies = DependencyUtils.collectDirectDependencies(root.getChildren());
        JsonUtils jsonUtils = new JsonUtils();
        return jsonUtils.artifactsToJson(directDependencies);
    }

    protected DependencyNode getDependencyNode(PreorderNodeListGenerator nlg) throws Exception {
        CollectRequest collectRequest = DependencyUtils.getCollectRequest(project, repos);
        DependencyNode root = system.collectDependencies(session, collectRequest).getRoot();
        DependencyRequest dependencyRequest = new DependencyRequest(root, null);
        system.resolveDependencies(session, dependencyRequest);
        root.accept(nlg);
        return root;
    }

    protected void prettyPrint0End() throws Exception {
        getLog().info(".");
        getLog().info("There are no dependencies in this project! - " + project.getGroupId() + "/" + project.getArtifactId() );
        getLog().info(".");
    }

    protected void prettyPrint(ProjectJsonResponse response) throws Exception {
        getLog().info(".");
        getLog().info("Project name: " + response.getName());
        getLog().info("Project id: "   + response.getId());
        getLog().info("Dependencies: " + response.getDep_number());
        getLog().info("Outdated: "     + response.getOut_number());
        getLog().info("");
        getLog().info("You can find your updated project here: " + baseUrl + "/user/projects/" + response.getId() );
        getLog().info("");
    }

    private void iterateThrough(List<Dependency> dependencies){
        for(Dependency dep: dependencies){
            getLog().info(" - dependency: " + dep.getGroupId() + "/" + dep.getArtifactId() + " " + dep.getVersion());
        }
    }

    private List<Plugin> getPluginsFromXml(){
        List<Plugin> plugins = new ArrayList<Plugin>();
        try {
            File pom = project.getModel().getPomFile();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pom);
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile("//plugins/plugin");

            NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            for (int i = 0 ; i < nl.getLength() ; i++){
                Node node = nl.item(i);
                Plugin plugin = new Plugin();
                fillPlugin(node, plugin);
                if (plugin.getGroupId() != null && plugin.getArtifactId() != null){
                    plugins.add(plugin);
                }
            }
        } catch (Exception exc){
            getLog().error(exc);
        }
        return plugins;
    }

    private void fillPlugin(Node node, Plugin plugin){
        for (int xi = 0 ; xi < node.getChildNodes().getLength() ; xi++ ){
            Node child = node.getChildNodes().item(xi);
            if (child == null){
                return ;
            }
            if (child.getNodeName().equals("groupId")){
                plugin.setGroupId(child.getTextContent().trim());
            }
            if (child.getNodeName().equals("artifactId")){
                plugin.setArtifactId(child.getTextContent().trim());
            }
            if (child.getNodeName().equals("version")){
                String version = parseVersionString( child.getTextContent().trim() );
                plugin.setVersion(version);
            }
        }
    }

    private String parseVersionString(String version){
        if (version.startsWith("${")){
            String verValue = version.replaceAll("\\$\\{", "").replaceAll("\\}", "");
            version = (String) project.getProperties().get(verValue);
        }
        return version;
    }

    private List<Dependency> filterForScopes(List<Dependency> dependencies){
        if (skipScopes == null || skipScopes.trim().isEmpty() || dependencies == null || dependencies.isEmpty())
            return dependencies;

        String[] scopes = skipScopes.split(",");
        List<Dependency> filtered = new ArrayList<Dependency>();
        for (Dependency dependency : dependencies){
            boolean ignoreScope = false;
            for ( String scope : scopes ) {
                if (scope != null &&
                    dependency != null &&
                    dependency.getScope() != null &&
                    dependency.getScope().toLowerCase().equals(scope.toLowerCase()))
                {
                    ignoreScope = true;
                }
            }
            if (ignoreScope == false ){
                filtered.add(dependency);
            }
        }
        return filtered;
    }

}
