package org.gatorgradle.internal;

import static org.gatorgradle.GatorGradlePlugin.*;

import org.gatorgradle.command.BasicCommand;
import org.gatorgradle.command.Command;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DependencyManager {
    /**
     * Install or Update the given dependency.
     *
     * @param  dep the dependency to update or install
     * @return     a boolean indicating success or failure
     */
    public static boolean installOrUpdate(Dependency dep) {
        switch (dep) {
            case GATORGRADER:
                return doGatorGrader();
            case PYTHON:
                return doPython();
            default:
                System.err.println("Unsupported Dependency: " + dep);
                return false;
        }
    }

    private static boolean hasPython3() {
        BasicCommand cmd = new BasicCommand("python3", "-V").outputToSysOut(false);
        cmd.run();
        if (cmd.exitValue() == 0 && cmd.getOutput().contains(" 3.")) {
            return true;
        }
        return false;
    }

    private static boolean doPython() {
        if (hasPython3()) {
            return true;
        }
        System.out.println(
            "You must install Python 3! Please visit https://www.python.org/ to get started!");
        return false;
    }

    private static boolean doGatorGrader() {
        if (OS.equals(WINDOWS)) {
            return doWindowsGatorGrader();
        } else if (OS.equals(MACOS)) {
            return doMacGatorGrader();
        } else {
            // assume linux if not windows or mac
            return doLinuxGatorGrader();
        }
    }

    private static boolean doWindowsGatorGrader() {
        System.err.println("Automated installation of GatorGrader unsupported for Windows!");
        System.err.println(
            "To install, run the following command after installing git (https://git-scm.com/downloads):");
        System.err.println(
            "git clone https://github.com/gkapfham/gatorgrader.git " + GATORGRADER_HOME);
        return false;
    }

    private static boolean doMacGatorGrader() {
        System.err.println("Automated installation of GatorGrader unsupported for MacOS!");
        System.err.println("To install, run the following commands:");
        System.err.println(
            "ruby -e \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)\"");
        System.err.println("brew update");
        System.err.println("brew install git");
        System.err.println(
            "git clone https://github.com/gkapfham/gatorgrader.git " + GATORGRADER_HOME);
        return false;
    }

    private static boolean doLinuxGatorGrader() {
        Path workingDir = Paths.get(GATORGRADER_HOME);

        boolean doDeps = false;

        // quick git pull installation
        BasicCommand updateOrInstall = new BasicCommand();
        updateOrInstall.outputToSysOut(true).setWorkingDir(workingDir.toFile());
        if (Files.exists(Paths.get(GATORGRADER_HOME))) {
            // gatorgrader repo exists (most likely)
            updateOrInstall.with("git", "pull");
            System.out.println("Updating GatorGrader...");
        } else {
            // make dirs
            boolean suc = workingDir.toFile().mkdirs();
            if (!suc) {
                System.err.println("Failed to make directories: " + workingDir);
            }
            // FIXME: will need to update url
            updateOrInstall.with(
                "git", "clone", "https://github.com/gkapfham/gatorgrader.git", GATORGRADER_HOME);

            // configure gatorgrader dependencies
            doDeps = true;
            System.out.println("Installing GatorGrader...");
        }

        // install gatorgrader, and block until complete (FIXME: this needs to be better)
        updateOrInstall.run(true);
        if (updateOrInstall.exitValue() != Command.SUCCESS) {
            System.err.println(
                "ERROR! GatorGrader management failed! Perhaps we couldn't find git?");
            System.err.println("Command run: " + updateOrInstall.getDescription());
            System.err.println("OUTPUT: " + updateOrInstall.getOutput().trim());
            return false;
        }

        if (doDeps) {
            // TODO: look into using pipenv or other virtual environment - can we activate those
            // environments from java and have it continue to be activated for subsequent commands?

            System.out.println("Installing GatorGrader dependencies...");
            BasicCommand dep =
                new BasicCommand("pip3", "install", "-r", GATORGRADER_HOME + "/requirements.txt");
            dep.outputToSysOut(true);
            dep.run();
            System.out.println("Finished GatorGrader install!");
            if (dep.exitValue() != 0) {
                System.err.println(
                    "ERROR! GatorGrader management failed! Could not install dependencies!");
                System.err.println("Command run: " + dep.getDescription());
                System.err.println("OUTPUT: " + dep.getOutput());
                return false;
            }
        }

        System.out.println();
        System.out.println();
        return true;
    }
}
