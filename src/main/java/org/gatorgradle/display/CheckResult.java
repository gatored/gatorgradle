package org.gatorgradle.display;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gatorgradle.command.Command;
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
  public static final String REACH_SYMBOL_RAW = "\u25CF"; // circle (●)

  public static final String PASS_SYMBOL =
      StringUtil.color(StringUtil.GOOD, PASS_SYMBOL_RAW);
  public static final String FAIL_SYMBOL =
      StringUtil.color(StringUtil.BAD, FAIL_SYMBOL_RAW);
  public static final String FIX_SYMBOL =
      StringUtil.color("\u001B[1;33m", FIX_SYMBOL_RAW);
  public static final String REACH_SYMBOL =
      StringUtil.color("\u001B[36m", REACH_SYMBOL_RAW);

  public String check;
  public Boolean outcome;
  public String diagnostic;

  public Command command;

  /**
   * Construct a CheckResult.
   *
   * @param command    the command run
   * @param check      the check completed
   * @param outcome    the outcome of the check
   * @param diagnostic the diagnostic output from the check
   **/
  public CheckResult(Command command, String check, Boolean outcome, String diagnostic) {
    this.command = command;
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
   * @param command the command run
   * @param json    the json to parse
   * @throws MalformedJsonException if the json given is not valid for a CheckResult
   *
   */
  public CheckResult(Command command, String json) throws MalformedJsonException {
    this.command = command;
    if (json == null) {
      throw new MalformedJsonException("Null JSON text", null);
    }
    // handle escaped quotes by replacing them with '
    json = json.replaceAll("\\\\\"", "'").trim();

    Matcher matcher = CHECK_REGEX.matcher(json);
    if (!matcher.matches()) {
      throw new MalformedJsonException("Could not find 'check' key in", json);
    }
    this.check = matcher.group(1);


    matcher.usePattern(OUTCOME_REGEX);
    matcher.reset();
    if (!matcher.matches()) {
      throw new MalformedJsonException("Could not find 'outcome' key in", json);
    }
    this.outcome = Boolean.parseBoolean(matcher.group(1));


    matcher.usePattern(DIAGNOSTIC_REGEX);
    matcher.reset();
    if (!matcher.matches()) {
      throw new MalformedJsonException("Could not find 'diagnostic' key in", json);
    }
    this.diagnostic = matcher.group(1);
  }

  /**
   * Tests equality, dependent on all member variables.
   *
   * @param otherObj the object to compare to
   *
   */
  public boolean equals(Object otherObj) {
    if (otherObj instanceof CheckResult) {
      CheckResult other = (CheckResult) otherObj;
      return this.check != null && this.check.equals(other.check)
        && this.outcome != null && this.outcome.equals(other.outcome)
        && this.diagnostic != null && this.diagnostic.equals(other.diagnostic)
        && this.command != null && this.command.equals(other.command);
    }
    return false;
  }

  public int hashCode() {
    return 17 * check.hashCode() + (outcome ? 5 : 7) + 73 * diagnostic.hashCode();
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
    } else if (check.contains("--reach")) {
      return REACH_SYMBOL + INDENT + check;
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

  /**
   * Returns a string representation of this result.
   *
   * @return a string
   **/
  public String jsonReport() {
    return "{\"check\":\"" + StringUtil.jsonEscape(check) + "\",\"outcome\":"
      + StringUtil.jsonEscape(outcome.toString().toLowerCase(Locale.ENGLISH))
      + ",\"diagnostic\":\"" + StringUtil.jsonEscape(diagnostic) + "\"}";
  }
}
