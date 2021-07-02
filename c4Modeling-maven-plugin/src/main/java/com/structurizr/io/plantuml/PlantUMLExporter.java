package com.structurizr.io.plantuml;

import com.structurizr.io.Diagram;
import com.structurizr.io.IndentingWriter;
import com.structurizr.model.*;
import com.structurizr.util.StringUtils;
import com.structurizr.view.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.java.Log;
import org.javatuples.Pair;

import java.net.URI;
import java.util.*;

import static java.lang.String.format;

@Log
@Accessors(fluent = true)
public class PlantUMLExporter extends AbstractPlantUMLExporter {

    private static final String ROOT_INCLUDES = "https://raw.githubusercontent.com/uniknow/c4Modeling/master/includes";

    /*
     * List of URLs which need to be included in generated plantUML file
     */
    private final List<Pair<String, URI>> customIncludeURLs = new ArrayList<>();

    /*
     * List of files that need to be included in generated plantUML file
     */
    private final List<Pair<String, String>> customIncludeFile = new ArrayList<>();

    /*
     * Specifies whether legend will be added to generated diagrams
     */
    @Getter
    @Setter
    private boolean addLegend = false;

    private int groupId = 0;

    public PlantUMLExporter() {
    }

    @Override
    protected boolean isAnimationSupported(View view) {
        //return !(view instanceof DynamicView);
        return true;
    }

    public Diagram export(DynamicView view) {
        if (!this.isUseSequenceDiagrams()) {
            return super.export(view);
        } else {
            IndentingWriter writer = new IndentingWriter();
            this.writeHeader(view, writer);
            Set<Element> elements = new LinkedHashSet<>();
            Iterator var4 = view.getRelationships().iterator();

            while (var4.hasNext()) {
                RelationshipView relationshipView = (RelationshipView) var4.next();
                elements.add(relationshipView.getRelationship().getSource());
                elements.add(relationshipView.getRelationship().getDestination());
            }

            var4 = elements.iterator();

            while (var4.hasNext()) {
                Element element = (Element) var4.next();
                this.writeElement(view, element, writer);
            }

            this.writeRelationships(view, writer);
            this.writeFooter(view, writer);
            return new Diagram(view, writer.toString());
        }
    }

    @Override
    public void addIncludeURL(URI uri, String id) {
        if (customIncludeURLs.stream().noneMatch(pair -> uri.equals(pair.getValue1()))) {
            log.info("Adding " + uri + " to " + customIncludeURLs);
            customIncludeURLs.add(Pair.with(id, uri));
        }
    }

    @Override
    public void addIncludeFile(String file, String id) {
        if (customIncludeFile.stream().noneMatch(pair -> file.equals(pair.getValue1()))) {
            customIncludeFile.add(Pair.with(id, file));
        }
    }

    @Override
    protected void writeHeader(View view, IndentingWriter writer) {
        clearIncludes();

        addRequiredIncludes(view);

        customIncludeURLs.forEach(includeURL -> super.addIncludeURL(includeURL.getValue1(), includeURL.getValue0()));
        customIncludeFile.forEach(include -> super.addIncludeFile(include.getValue1(), include.getValue0()));

        super.writeHeader(view, writer);

        if (addLegend) {
            writer.writeLine("LAYOUT_WITH_LEGEND()");
        }

        writer.writeLine();
    }

    /**
     * Adds the includes which are necessary for the specified view.
     */
    private void addRequiredIncludes(View view) {
        addIncludeURL(URI.create(ROOT_INCLUDES + "/C4.puml"));
        addIncludeURL(URI.create(ROOT_INCLUDES + "/C4_Context.puml"));

        if (view.getElements().stream().map(ElementView::getElement).anyMatch(e -> e instanceof Container || e instanceof ContainerInstance)) {
            addIncludeURL(URI.create(ROOT_INCLUDES + "/C4_Container.puml"));
        }

        if (view.getElements().stream().map(ElementView::getElement).anyMatch(e -> e instanceof Component)) {
            addIncludeURL(URI.create(ROOT_INCLUDES + "/C4_Component.puml"));
            addIncludeURL(URI.create(ROOT_INCLUDES + "/C4_UseCase.puml"));
        }

        if (view instanceof DeploymentView) {
            addIncludeURL(URI.create(ROOT_INCLUDES + "/C4_Deployment.puml"));
        }
    }

    @Override
    protected void startEnterpriseBoundary(String enterpriseName, IndentingWriter writer) {
        writer.writeLine(String.format("Enterprise_Boundary(enterprise, \"%s\") {", enterpriseName));
        writer.indent();
    }

    @Override
    protected void endEnterpriseBoundary(IndentingWriter writer) {
        writer.outdent();
        writer.writeLine("}");
        writer.writeLine();
    }

    @Override
    protected void startGroupBoundary(String group, IndentingWriter writer) {
        writer.writeLine(String.format("Boundary(group_%s, \"%s\") {", groupId++, group));
        writer.indent();
    }

    @Override
    protected void endGroupBoundary(IndentingWriter writer) {
        writer.outdent();
        writer.writeLine("}");
        writer.writeLine();
    }

    @Override
    protected void startSoftwareSystemBoundary(View view, SoftwareSystem softwareSystem, IndentingWriter writer) {
        writer.writeLine(String.format("System_Boundary(\"%s_boundary\", \"%s\") {", softwareSystem.getId(), softwareSystem.getName()));
        writer.indent();
    }

    @Override
    protected void endSoftwareSystemBoundary(IndentingWriter writer) {
        writer.outdent();
        writer.writeLine("}");
        writer.writeLine();
    }

    @Override
    protected void startContainerBoundary(View view, Container container, IndentingWriter writer) {
        writer.writeLine(String.format("Container_Boundary(\"%s_boundary\", \"%s\") {", container.getId(), container.getName()));
        writer.indent();
    }

    @Override
    protected void endContainerBoundary(IndentingWriter writer) {
        writer.outdent();
        writer.writeLine("}");
        writer.writeLine();
    }

    @Override
    protected void startDeploymentNodeBoundary(DeploymentView view, DeploymentNode deploymentNode, IndentingWriter writer) {
        if (StringUtils.isNullOrEmpty(deploymentNode.getTechnology())) {
            writer.writeLine(
                    format("Deployment_Node(%s, \"%s\") {",
                            deploymentNode.getId(),
                            deploymentNode.getName() + (deploymentNode.getInstances() > 1 ? " (x" + deploymentNode.getInstances() + ")" : "")
                    )
            );
        } else {
            writer.writeLine(
                    format("Deployment_Node(%s, \"%s\", \"%s\") {",
                            deploymentNode.getId(),
                            deploymentNode.getName() + (deploymentNode.getInstances() > 1 ? " (x" + deploymentNode.getInstances() + ")" : ""),
                            deploymentNode.getTechnology()
                    )
            );
        }
        writer.indent();

        if (!isVisible(view, deploymentNode)) {
            writer.writeLine("hide " + deploymentNode.getId());
        }
    }

    @Override
    protected void endDeploymentNodeBoundary(IndentingWriter writer) {
        writer.outdent();
        writer.writeLine("}");
        writer.writeLine();
    }

    @Override
    protected void writeElement(View view, Element element, IndentingWriter writer) {
        Element elementToWrite = element;
        String id = element.getId();

        if (element instanceof StaticStructureElementInstance) {
            StaticStructureElementInstance elementInstance = (StaticStructureElementInstance) element;
            element = elementInstance.getElement();
        }

        String name = element.getName();
        String description = element.getDescription();

        if (StringUtils.isNullOrEmpty(description)) {
            description = "";
        }

        if (element instanceof Person) {
            Person person = (Person) element;
            if (person.getLocation() == Location.External) {
                writer.writeLine(String.format("Person_Ext(%s, \"%s\", \"%s\")", id, name, description));
            } else {
                writer.writeLine(String.format("Person(%s, \"%s\", \"%s\")", id, name, description));
            }
        } else if (element instanceof SoftwareSystem) {
            SoftwareSystem softwareSystem = (SoftwareSystem) element;
            if (softwareSystem.getLocation() == Location.External) {
                writer.writeLine(String.format("System_Ext(%s, \"%s\", \"%s\")", id, name, description));
            } else {
                writer.writeLine(String.format("System(%s, \"%s\", \"%s\")", id, name, description));
            }
        } else if (element instanceof Container) {
            Container container = (Container) element;
            ElementStyle elementStyle = view.getViewSet().getConfiguration().getStyles().findElementStyle(element);
            String shape = "";
            if (elementStyle.getShape() == Shape.Cylinder) {
                shape = "Db";
            } else if (elementStyle.getShape() == Shape.Pipe) {
                shape = "Queue";
            }

            if (StringUtils.isNullOrEmpty(container.getTechnology())) {
                writer.writeLine(String.format("Container%s(%s, \"%s\", \"%s\")", shape, id, name, description));
            } else {
                writer.writeLine(String.format("Container%s(%s, \"%s\", \"%s\", \"%s\")", shape, id, name, container.getTechnology(), description));
            }
        } else if (element instanceof Component) {
            Component component = (Component) element;
            if (component.getTagsAsSet().contains("USE-CASE")) {
                writer.writeLine(String.format("UseCase(%s, \"%s\", \"%s\", \"%s\")", id, name, component.getTechnology(), description));
            } else {
                if (StringUtils.isNullOrEmpty(component.getTechnology())) {
                    writer.writeLine(String.format("Component(%s, \"%s\", \"%s\")", id, name, description));
                } else {
                    log.info("technology is " + component.getTechnology());
                    writer.writeLine(String.format("Component(%s, \"%s\", \"%s\", \"%s\")", id, name, component.getTechnology(), description));
                }
            }
        } else if (element instanceof InfrastructureNode) {
            InfrastructureNode infrastructureNode = (InfrastructureNode) element;
            if (StringUtils.isNullOrEmpty(infrastructureNode.getTechnology())) {
                writer.writeLine(format("Deployment_Node(%s, \"%s\")", infrastructureNode.getId(), name));
            } else {
                if (StringUtils.isNullOrEmpty(infrastructureNode.getTechnology())) {
                    writer.writeLine(format("Deployment_Node(%s, \"%s\", \"%s\")", infrastructureNode.getId(), name, infrastructureNode.getTechnology()));
                }
            }
        }

        if (!isVisible(view, elementToWrite)) {
            writer.writeLine("hide " + id);
        }
    }

    @Override
    protected void writeRelationship(View view, RelationshipView relationshipView, IndentingWriter writer) {
        Relationship relationship = relationshipView.getRelationship();
        Element source = relationship.getSource();
        Element destination = relationship.getDestination();

        if (relationshipView.isResponse() != null && relationshipView.isResponse()) {
            source = relationship.getDestination();
            destination = relationship.getSource();
        }

        String description = "";

        if (!StringUtils.isNullOrEmpty(relationshipView.getOrder())) {
            description = relationshipView.getOrder() + ". ";
        }

        description += (hasValue(relationshipView.getDescription()) ? relationshipView.getDescription() : hasValue(relationshipView.getRelationship().getDescription()) ? relationshipView.getRelationship().getDescription() : "");

        if (StringUtils.isNullOrEmpty(relationship.getTechnology())) {
            writer.writeLine(format("Rel_D(%s, %s, \"%s\")", source.getId(), destination.getId(), description));
        } else {
            writer.writeLine(format("Rel_D(%s, %s, \"%s\", \"%s\")", source.getId(), destination.getId(), description, relationship.getTechnology()));
        }
    }

}