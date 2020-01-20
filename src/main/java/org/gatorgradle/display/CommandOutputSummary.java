package org.gatorgradle.display;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.gatorgradle.GatorGradlePlugin;
import org.gatorgradle.command.BasicCommand;
import org.gatorgradle.command.Command;
import org.gatorgradle.command.GatorGraderCommand;
import org.gatorgradle.config.GatorGradleConfig;
import org.gatorgradle.util.StringUtil;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;

public class CommandOutputSummary {
  private static final String YES = StringUtil.color(StringUtil.GOOD, "Yes");
  private static final String NO = StringUtil.color(StringUtil.BAD, "No");

  private List<CheckResult> completedChecks;
  private final Logger log;

  /**
   * Create an empty output summary.
   *
   * @param log the Logger to use
   */
  public CommandOutputSummary(Logger log) {
    this.completedChecks = new ArrayList<>();
    this.log = log;
  }

  /**
   * Create an output summary from the given commands.
   *
   * @param completedCommands the commands to build a summary of
   * @param log the Logger to use
   */
  public CommandOutputSummary(List<BasicCommand> completedCommands, Logger log) {
    this.completedChecks = completedCommands.stream()
                           .map(cmd -> parseCommandOutput(cmd, false))
                           .collect(Collectors.toList());
    this.log = log;
  }

  /**
   * Add the command to the summary.
   *
   * @param cmd the command to add
   */
  public void addCompletedCommand(Command cmd) {
    CheckResult result = parseCommandOutput((BasicCommand)cmd, false);
    if (!completedChecks.contains(result)) {
      completedChecks.add(result);
      showInProgressSummary(result);
    } else {
      log.info("Duplicate results from: " + cmd.toString());
    }
  }

  public int getNumCompletedTasks() {
    return completedChecks.size();
  }

  boolean nomore = false;

  /**
   * Output a description of what just finished, or maybe a status.
   *
   * @param result the check that just finished
   */
  public void showInProgressSummary(CheckResult result) {
    if (nomore) {
      return;
    }
    printResult(result, false);
    if (result.outcome == false && GatorGradleConfig.get().shouldFastBreakBuild()) {
      log.lifecycle("\n  -~-  \u001B[1;31mCHECKS FAILED\u001B[0m  -~-\n");
      nomore = true;
      throw new GradleException("Check failed!");
    }
  }

  private void printResult(CheckResult result, boolean includeDiagnostic) {
    // debug output for TAs
    log.debug("COMMAND: '{}'", result.command.toString());
    log.debug("EXIT VALUE: '{}'", result.command.exitValue());
    log.debug("OUTPUT:");

    // actual output of the command should be parsed and colored, etc
    log.lifecycle(result.textReport(includeDiagnostic));
  }

  /**
   * Output the compiled summary to the project's configured endpoint, if existant.
   *
   *
   * @param failed the failed check results
   * @param all    the completed check results
   */
  public void uploadOutputSummary(List<CheckResult> failed, List<CheckResult> all) {
    StringBuilder builder = new StringBuilder();
    builder.append('{');

    String userId;
    BasicCommand getUserId = null;
    if (GatorGradlePlugin.OS.equals(GatorGradlePlugin.WINDOWS)) {
      getUserId = new BasicCommand(
          "sh", "/C", GatorGradleConfig.get().getIdCommand());
    } else {
      getUserId = new BasicCommand(
          "sh", "-c", GatorGradleConfig.get().getIdCommand());
    }
    getUserId.run();
    if (getUserId.exitValue() == Command.SUCCESS) {
      userId = getUserId.getOutput().trim();
    } else {
      throw new GradleException("User ID command failed");
    }

    builder.append("\"userId\":");
    builder.append('\"').append(userId).append('\"').append(',');

    builder.append("\"time\":");
    builder.append('\"').append(Instant.now()).append('\"').append(',');

    builder.append("\"assignment\":");
    builder.append('\"').append(GatorGradleConfig.get().getAssignmentName());
    builder.append('\"').append(',');

    // reflection
    builder.append("\"reflection\":");
    String reflection = "";
    try {
      reflection = StringUtil.jsonEscape(
        String.join(
          "\n",
          Files.readAllLines(
              Paths.get(
                  GatorGradleConfig.get().getReflectionPath()
              )
          )
        )
      );
    } catch (IOException ex) {
      throw new GradleException(
          "Exception while reading reflection file "
          + GatorGradleConfig.get().getReflectionPath(), ex);
    }
    builder.append('\"').append(reflection).append('\"').append(',');
    // end reflection

    // report
    builder.append("\"report\":{");

    builder.append("\"numberOfChecks\":");
    builder.append(Integer.toString(all.size())).append(',');

    builder.append("\"numberOfFailures\":");
    builder.append(Integer.toString(failed.size())).append(',');

    builder.append("\"results\":").append('[');
    builder.append(
        String.join(
          ",",
          all.stream().map(res -> res.jsonReport())
          .collect(Collectors.toList())
        )
    );
    builder.append(']').append('}');
    // end report

    builder.append('}');

    String resultListJson = builder.toString();

    log.info("Result JSON: {}", resultListJson);

    HttpURLConnection con = null;
    // get report endpoint and api key from environment variable
    String endpoint = GatorGradleConfig.get().getReportEndpoint();
    String apikey = GatorGradleConfig.get().getReportApiKey();
    log.info("Uploading report to {} with key {}", endpoint, apikey);
    try {
      if (endpoint == null || endpoint.isEmpty()) {
        throw new GradleException("No report endpoint specified");
      } else if (apikey == null || apikey.isEmpty()) {
        throw new GradleException("No API key specified");
      }
      URL url = new URL(endpoint);
      con = (HttpURLConnection) url.openConnection();
      con.setRequestMethod("POST");
      con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
      con.setRequestProperty("Accept", "application/json");
      con.setRequestProperty("x-api-key", apikey);
      con.setUseCaches(false);
      con.setDoInput(true);
      con.setDoOutput(true);
      con.connect();

      // send request
      try (OutputStream os = con.getOutputStream()) {
        byte[] input = resultListJson.getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
        log.info("Compiled JSON to send: {}", resultListJson);
      }

      // get response
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
        StringBuilder response = new StringBuilder();
        String responseLine = null;
        while ((responseLine = br.readLine()) != null) {
          response.append(responseLine.trim());
        }
        log.info("Got data upload response: {}", response.toString());
      }

      if (con.getResponseCode() == 200) {
        log.lifecycle("Report uploaded successfully");
      }

    } catch (MalformedURLException ex) {
      throw new GradleException(
          "Failed to upload data; configured report endpoint ("
          + endpoint + ") is malformed", ex);
    } catch (IOException ex) {
      throw new GradleException("Exception while uploading check data", ex);
    } finally {
      if (con != null) {
        con.disconnect();
      }
    }
  }

  /**
   * Use the internal member variables to call the previous uploadOutputSummary.
   */
  public void uploadOutputSummary() {
    List<CheckResult> failed = completedChecks.stream()
                               .filter(result -> result.outcome == false)
                               .collect(Collectors.toList());
    uploadOutputSummary(failed, completedChecks);
  }

  /**
   * Output the compiled summary to the project's Logger.
   */
  public void showOutputSummary() {
    // log.lifecycle("\n\n  -~-  \u001B[1;36mBeginning check summary\u001B[1;0m  -~-\n\n");
    int totalChecks = getNumCompletedTasks();
    List<CheckResult> failed = completedChecks.stream()
                               .filter(result -> result.outcome == false)
                               .collect(Collectors.toList());
    boolean isFailure = failed.size() > 0;

    if (isFailure) {
      log.lifecycle("\n\n\u001B[1;33m-~-  \u001B[1;31mFAILURES  \u001B[1;33m-~-\u001B[0m\n");
      for (int i = 0; i < failed.size(); i++) {
        printResult(failed.get(i), true);
      }
    }

    int passedChecks = totalChecks - failed.size();

    StringUtil.border("Passed " + passedChecks + "/" + totalChecks + " ("
            + (Math.round((passedChecks * 100) / (float) totalChecks)) + "%)"
            + " of checks for " + GatorGradleConfig.get().getAssignmentName() + "!",
        isFailure ? "\u001B[1;31m" : "\u001B[1;32m",
        isFailure ? "\u001B[1;35m" : "\u001B[1;32m", log);

    if (isFailure && GatorGradleConfig.get().shouldBreakBuild()) {
      throw new GradleException(
          StringUtil.color(StringUtil.BAD, "Grading checks failed -- scroll up for failures"));
    }
  }

  private CheckResult parseGatorGraderCommand(GatorGraderCommand cmd, boolean includeDiagnostic) {
    CheckResult result = null;
    String output = cmd.getOutput();
    try {
      result = new CheckResult(cmd, output);
    } catch (CheckResult.MalformedJsonException ex) {

      // test if the problem was an unsupported argument
      int index = -1;
      String unrec = "gatorgrader.py: error: unrecognized arguments:";
      if ((index = output.indexOf(unrec)) >= 0) {
        index += unrec.length();
        unrec = output.substring(index).trim();
        result = new CheckResult(
            cmd,
            "Unrecognized GatorGrader check",
            false,
            "The " + unrec + " check is not supported"
        );
      } else if (output.contains("--reach")) {
        result = new CheckResult(
            cmd,
            "Contains --reach",
            true,
            "this might work"
          );
      } else {
        if (!includeDiagnostic) {
          log.error("{} errored: \'{}\'", cmd.toString(), ex.getMessage());
        }
        result = new CheckResult(
            cmd,
            "Unknown GatorGrader check",
            false,
            "The check failed with an unknown error"
        );
      }
    }
    return result;
  }

  private CheckResult parseCommandLineExecutable(BasicCommand cmd, boolean includeDiagnostic) {
    String output = cmd.getOutput();
    StringBuilder diagnostic = new StringBuilder();
    if (includeDiagnostic && output != null && !output.trim().isEmpty()) {
      Scanner scan = new Scanner(output);
      diagnostic.append(cmd.executable() + " diagnostics:\n");
      while (scan.hasNext()) {
        String line = scan.nextLine().trim();
        if (!line.isEmpty()) {
          diagnostic.append(line).append("\n");
        }
      }
    } else {
      diagnostic.append("No diagnostic available");
    }
    return new CheckResult(
        cmd,
        "The file " + cmd.last() + " passes " + cmd.executable(),
        cmd.exitValue() == Command.SUCCESS,
        diagnostic.toString().trim()
    );
  }

  private CheckResult parsePureCommandOutput(BasicCommand cmd, boolean includeDiagnostic) {
    String output = cmd.getOutput();
    StringBuilder diagnostic = new StringBuilder();
    if (includeDiagnostic && output != null && !output.trim().isEmpty()) {
      Scanner scan = new Scanner(output);
      diagnostic.append(cmd + " printed:\n");
      while (scan.hasNext()) {
        String line = scan.nextLine().trim();
        if (!line.isEmpty()) {
          diagnostic.append(line).append("\n");
        }
      }
    } else {
      diagnostic.append("No diagnostic available");
    }
    return new CheckResult(
        cmd,
        cmd.toString() + " executes",
        cmd.exitValue() == Command.SUCCESS,
        diagnostic.toString().trim()
    );
  }

  private CheckResult parseCommandOutput(BasicCommand cmd, boolean includeDiagnostic) {
    CheckResult result;
    if (cmd instanceof GatorGraderCommand) {
      result = parseGatorGraderCommand((GatorGraderCommand) cmd, includeDiagnostic);
    } else if (GatorGradleConfig.get().isCommandLineExecutable(cmd.executable())) {
      result = parseCommandLineExecutable(cmd, includeDiagnostic);
    } else {
      result = parsePureCommandOutput(cmd, includeDiagnostic);
    }

    return result;
  }
}
