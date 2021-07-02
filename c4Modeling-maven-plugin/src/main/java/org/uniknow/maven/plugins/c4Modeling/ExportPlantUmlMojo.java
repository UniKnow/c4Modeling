package org.uniknow.maven.plugins.c4Modeling;

import com.structurizr.Workspace;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.io.Diagram;
import com.structurizr.io.plantuml.PlantUMLExporter;
import com.structurizr.util.WorkspaceUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;

@Mojo(name = "plantUml", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class ExportPlantUmlMojo extends AbstractMojo {

    /**
     * Path to the workspace JSON file/DSL file(s)
     */
    @Parameter(name = "model", required = true)
    private String model;

    /**
     * Path to file to include within generated PlantUML files
     */
    @Parameter(name = "include")
    private String include;

    /**
     * Boolean indicating whether legend should be added
     */
    @Parameter(name = "legend")
    private boolean legend = false;
    /**
     * Path where diagrams will be exported
     */
    @Parameter(name = "output", required = true)
    private String output;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        Workspace workspace;

        try {
            getLog().info("Exporting workspace from " + model);
            if (model.endsWith(".json")) {
                getLog().info("Loading workspace from JSON");
                workspace = WorkspaceUtils.loadWorkspaceFromJson(new File(model));
            } else {
                getLog().info("Loading workspace from DSL");
                StructurizrDslParser structurizrDslParser = new StructurizrDslParser();
                structurizrDslParser.parse(new File(model));
                workspace = structurizrDslParser.getWorkspace();
            }

            PlantUMLExporter plantUMLExporter = new PlantUMLExporter();
            plantUMLExporter.addLegend(legend);

            // add plantuml include file to customizing (optional)
            if (include != null) {
                getLog().info("Including file " + include);
                plantUMLExporter.addIncludeFile(include);
            }

            if (workspace.getViews().isEmpty()) {
                getLog().warn("The workspace contains no views");
            } else {
                plantUMLExporter.setUseSequenceDiagrams(false);
                Collection<Diagram> diagrams = plantUMLExporter.export(workspace);

                File outputDir = new File(output);
                outputDir.mkdirs();

                for (Diagram diagram : diagrams) {
                    File file = new File(output, String.format("%s.puml", diagram.getKey()));
                    writeToFile(file, diagram.getDefinition());

                    if (!diagram.getFrames().isEmpty()) {
                        int index = 1;
                        for (Diagram frame : diagram.getFrames()) {
                            file = new File(output, String.format("%s-%s.puml", diagram.getKey(), index));
                            writeToFile(file, frame.getDefinition());
                            index++;
                        }
                    }
                }
            }

        } catch (Exception error) {
            throw new MojoExecutionException(error.getMessage());
        }

    }

    private void writeToFile(File file, String content) throws Exception {
        System.out.println(" - writing " + file.getCanonicalPath());

        BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
        writer.write(content);
        writer.close();
    }
}
