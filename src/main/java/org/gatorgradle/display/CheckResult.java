package org.gatorgradle.display;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gatorgradle.util.StringUtil;

public class CheckResult {
  public static class MalformedJsonException extends Exception {
    public MalformedJsonException(String json) {
      super("Failed to parse json:\n---\n" + json + "\n---");
    }

    public MalformedJsonException(String reason, String json) {
      super("Failed to parse json -- " + reason
          + (json != null ? ":\n---\n" + json + "\n---" : ""));
    }
  }

  public static final Pattern CHECK_REGEX =
      Pattern.compile(".*\"check\":\\s*\"(.*?)\".*", Pattern.DOTALL);
  public static final Pattern OUTCOME_REGEX =
      Pattern.compile(".*\"outcome\":\\s*([Tt]rue|[Ff]alse).*", Pattern.DOTALL);
  public static final Pattern DIAGNOSTIC_REGEX =
      Pattern.compile(".*\"diagnostic\":\\s*\"(.*?)\".*", Pattern.DOTALL);

  public static final String INDENT = "  ";
  public static final String CONTINUE_SYMBOL_RAW = "\u2503"; // vertical line (┃)
  public static final String PASS_SYMBOL_RAW = "\u2714"; // heavy check (✔)
  public static final String FAIL_SYMBOL_RAW = "\u2718"; // heavy cross (✘)
  public static final String FIX_SYMBOL_RAW = "\u2794"; // right arrow (➔)

  public static final String PASS_SYMBOL =
      StringUtil.color(StringUtil.GOOD, PASS_SYMBOL_RAW);
  public static final String FAIL_SYMBOL =
      StringUtil.color(StringUtil.BAD, FAIL_SYMBOL_RAW);
  public static final String FIX_SYMBOL =
      StringUtil.color("\u001B[1;33m", FIX_SYMBOL_RAW);

  public String check;
  public Boolean outcome;
  public String diagnostic;

  /**
   * Construct a CheckResult.
   *
   * @param check      the check completed
   * @param outcome    the outcome of the check
   * @param diagnostic the diagnostic output from the check
   **/
  public CheckResult(String check, Boolean outcome, String diagnostic) {
    this.check = check;
    this.outcome = outcome;
    this.diagnostic = diagnostic;
  }

  /**
   * Parses the given JSON string, setting check, outcome,
   * and diagnostic member variables when found accordingly.
   *
   * <p>This method assumes the json passed represents only one
   * result, and any duplicates will overwrite values.
   *
   * @param parsing the json to parse
   * @throws MalformedJsonException if the json given is not valid for a CheckResult
   *
   */
  public CheckResult(String parsing) throws MalformedJsonException {
    if (parsing == null) {
      throw new MalformedJsonException("Null JSON text", null);
    }
    // handle escaped quotes by replacing them with '
    parsing = parsing.replaceAll("\\\\\"", "'").trim();

    Matcher matcher = CHECK_REGEX.matcher(parsing);
    if (!matcher.matches()) {
      throw new MalformedJsonException("Could not find 'check' key in", parsing);
    }
    this.check = matcher.group(1);


    matcher.usePattern(OUTCOME_REGEX);
    matcher.reset();
    if (!matcher.matches()) {
      throw new MalformedJsonException("Could not find 'outcome' key in", parsing);
    }
    this.outcome = Boolean.parseBoolean(matcher.group(1));


    matcher.usePattern(DIAGNOSTIC_REGEX);
    matcher.reset();
    if (!matcher.matches()) {
      throw new MalformedJsonException("Could not find 'diagnostic' key in", parsing);
    }
    this.diagnostic = matcher.group(1);
  }


  /**
   * Returns a string representation of this result.
   *
   * @param includeDiagnostic include diagnostic?
   * @return a string
   **/
  public String textReport(boolean includeDiagnostic) {
    if (outcome) {
      return PASS_SYMBOL + INDENT + check;
    } else {
      String output = FAIL_SYMBOL + INDENT + check;
      if (includeDiagnostic) {
        output += "\n " + INDENT + FIX_SYMBOL + INDENT + StringUtil.color(StringUtil.FIX,
            diagnostic.trim().replaceAll("\\n", "\n")
            .replaceAll("\n", "\n" + INDENT + INDENT + CONTINUE_SYMBOL_RAW + INDENT)
        );
      }
      return output;
    }
  }
}
