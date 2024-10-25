package org.gbif.pipelines.maven;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.gbif.occurrence.download.hive.OccurrenceAvroHdfsTableDefinition;

@Mojo(name = "avroschemageneration", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class OccurrenceAvscGeneratorMojo extends AbstractMojo {

  @Parameter(property = "avroschemageneration.pathToWrite")
  private String pathToWrite;

  public void setPathToWrite(String pathToWrite) {
    this.pathToWrite = pathToWrite;
  }

  @Override
  public void execute() throws MojoExecutionException {

    var path = Paths.get(pathToWrite);
    var schema = OccurrenceAvroHdfsTableDefinition.avroDefinition();

    getLog().info("Create occurrence avro schema - " + path);
    try {
      var header =
          "/** Autogenerated by maven-occurrence-avsc-schema-generator. DO NOT EDIT DIRECTLY */\n";
      var body = schema.toString(true);
      var result = header + body;

      Files.write(path, result.getBytes(UTF_8));
    } catch (IOException ex) {
      throw new MojoExecutionException(ex);
    }
  }
}
