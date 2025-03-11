package org.datatransferproject.fuzzer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.cloud.local.LocalJobStore;
import org.datatransferproject.datatransfer.generic.BlobbySerializer;
import org.datatransferproject.datatransfer.generic.GenericFileImporter;
import org.datatransferproject.datatransfer.generic.GenericImporter;
import org.datatransferproject.datatransfer.generic.SocialPostsSerializer;
import org.datatransferproject.launcher.monitor.ConsoleMonitor;
import org.datatransferproject.launcher.monitor.ConsoleMonitor.Level;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.transfer.idempotentexecutor.InMemoryIdempotentImportExecutor;
import org.datatransferproject.types.common.models.ContainerResource;
import org.datatransferproject.types.common.models.DataVertical;
import org.datatransferproject.types.common.models.blob.BlobbyStorageContainerResource;
import org.datatransferproject.types.common.models.blob.DigitalDocumentWrapper;
import org.datatransferproject.types.common.models.blob.DtpDigitalDocument;
import org.datatransferproject.types.common.models.social.SocialActivityActor;
import org.datatransferproject.types.common.models.social.SocialActivityContainerResource;
import org.datatransferproject.types.common.models.social.SocialActivityLocation;
import org.datatransferproject.types.common.models.social.SocialActivityModel;
import org.datatransferproject.types.common.models.social.SocialActivityType;
import org.datatransferproject.types.transfer.auth.AppCredentials;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

class RunConfig {
  private DataVertical vertical;
  private URL endpoint;

  public RunConfig(DataVertical vertical, URL endpoint) {
    this.vertical = vertical;
    this.endpoint = endpoint;
  }

  public DataVertical getVertical() {
    return vertical;
  }

  public URL getEndpoint() {
    return endpoint;
  }
}

public class Main {
  Monitor monitor;

  InputStream getResourceAsStream(String name) {
    return Main.class.getClassLoader().getResourceAsStream(name);
  }

  Main(Monitor monitor) {
    this.monitor = monitor;
  }

  private URL urlAppend(URL base, String suffix) throws MalformedURLException, URISyntaxException {
    String path = base.getPath();
    if (!path.endsWith("/")) {
      path += "/";
    }
    path += suffix;
    return base.toURI().resolve(path).toURL();
  }

  boolean hasHelpArg(String args[], Option helpOption) throws ParseException {
    var options = new Options();
    options.addOption(helpOption);
    return (new DefaultParser()).parse(options, args).hasOption(helpOption);
  }

  RunConfig parseArgs(String[] args) throws ParseException {
    var options = new Options();
    var dataTypeOption =
        Option.builder()
            .argName("Data Type")
            .required()
            .hasArg()
            .option("d")
            .longOpt("data-type")
            .desc("The data type to call the endpoint with")
            .converter(
                val -> {
                  try {
                    return DataVertical.valueOf(val.toUpperCase());
                  } catch (IllegalArgumentException e) {
                    var ex = new ParseException(String.format("Invalid Data Type: %s", val));
                    ex.initCause(e);
                    throw ex;
                  }
                })
            .build();
    options.addOption(dataTypeOption);
    var endpointOption =
        Option.builder()
            .argName("Endpoint Base")
            .required()
            .hasArg()
            .option("e")
            .longOpt("endpoint")
            .desc(
                "The endpoint to call. Will be extended with e.g. /blobs or /social-posts for BLOBS"
                    + " or SOCIAL_POSTS data-types.")
            .converter(
                val -> {
                  try {
                    return new URL(val);
                  } catch (MalformedURLException e) {
                    var ex = new ParseException(String.format("Invalid endpoint URL: %s", val));
                    ex.initCause(e);
                    throw ex;
                  }
                })
            .build();
    options.addOption(endpointOption);

    var parser = new DefaultParser();
    CommandLine parsedArgs;
    try {
      parsedArgs = parser.parse(options, args);
      DataVertical vertical = parsedArgs.getParsedOptionValue(dataTypeOption);
      URL endpoint = parsedArgs.getParsedOptionValue(endpointOption);
      return new RunConfig(vertical, endpoint);
    } catch (ParseException e) {
      var helpFormatter = new HelpFormatter();
      helpFormatter.printHelp("Generic Importer Fuzzer", options);
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  <T extends ContainerResource> GenericImporter<T, ?> buildImporter(
      DataVertical vertical, URL baseEndpoint, JobStore jobStore)
      throws MalformedURLException, URISyntaxException {
    var appCredentials = new AppCredentials("fuzz", "fuzz");
    switch (vertical) {
      case BLOBS:
        return (GenericFileImporter<T, ?>)
            new GenericFileImporter<BlobbyStorageContainerResource, BlobbySerializer.ExportData>(
                BlobbySerializer::serialize,
                appCredentials,
                urlAppend(baseEndpoint, "blobs"),
                jobStore,
                monitor);
      case SOCIAL_POSTS:
        return (GenericImporter<T, ?>)
            new GenericImporter<SocialActivityContainerResource, SocialPostsSerializer.ExportData>(
                SocialPostsSerializer::serialize,
                appCredentials,
                urlAppend(baseEndpoint, "social-posts"),
                monitor);
      default:
        throw new IllegalArgumentException(String.format("Unsupported vertical %s", vertical));
    }
  }

  @SuppressWarnings("unchecked")
  <T extends ContainerResource> T buildData(DataVertical vertical, JobStore jobStore, UUID jobId)
      throws IOException {
    switch (vertical) {
      case BLOBS:
        jobStore.create(
            jobId,
            "bartxt",
            new ByteArrayInputStream(
                "Hello world\r\n\r\nFoo\0bar".getBytes(StandardCharsets.UTF_8)));
        jobStore.create(jobId, "dtinitpng", getResourceAsStream("dtinit.png"));
        return (T)
            new BlobbyStorageContainerResource(
                "root",
                "rootdir",
                List.of(
                    new DigitalDocumentWrapper(
                        new DtpDigitalDocument("foo.mp4", "2020-02-01T01:02:03Z", "image/png"),
                        "image/png",
                        "dtinitpng"),
                    new DigitalDocumentWrapper(
                        new DtpDigitalDocument("bar.txt", null, "text/plain"),
                        "text/plain",
                        "bartxt")),
                List.of());
      case SOCIAL_POSTS:
        return (T)
            new SocialActivityContainerResource(
                "activity-a",
                new SocialActivityActor("actor-a", "@a", null),
                List.of(
                    new SocialActivityModel(
                        "activitymodel-a",
                        Instant.now(),
                        SocialActivityType.POST,
                        List.of(),
                        new SocialActivityLocation(null, 10, 10),
                        "Hello world",
                        "Hi there!",
                        null)));
      default:
        throw new IllegalArgumentException(String.format("Unsupported vertical %s", vertical));
    }
  }

  <T extends ContainerResource> void run(RunConfig runConfig) throws Exception {
    var jobStore = new LocalJobStore();
    var idempotentExecutor = new InMemoryIdempotentImportExecutor(monitor);
    var initialAuthData =
        new TokensAndUrlAuthData(
            "accessToken", "refreshToken", "http://localhost:8080/auth/refresh");
    var jobId = UUID.randomUUID();
    idempotentExecutor.setJobId(jobId);

    GenericImporter<T, ?> importer =
        buildImporter(runConfig.getVertical(), runConfig.getEndpoint(), jobStore);
    T data = buildData(runConfig.getVertical(), jobStore, jobId);
    importer.importItem(jobId, idempotentExecutor, initialAuthData, data);
    if (idempotentExecutor.getErrors().size() > 0) {
      monitor.severe(() -> String.format("%d errors", idempotentExecutor.getErrors().size()));
    } else {
      monitor.info(() -> "Imported all items successfully");
    }
  }

  public static void main(String[] args) throws Exception {
    var monitor = new ConsoleMonitor(Level.INFO);
    var main = new Main(monitor);
    RunConfig runConfig;
    try {
      runConfig = main.parseArgs(args);
    } catch (ParseException e) {
      monitor.severe(() -> e.getMessage());
      System.exit(1);
      return;
    }

    monitor.info(
        () ->
            String.format(
                "vertical: %s, endpoint: %s", runConfig.getVertical(), runConfig.getEndpoint()));
    main.run(runConfig);
  }
}
