package moar.cli;

import static java.lang.Boolean.FALSE;
import static java.lang.Math.max;
import static java.lang.String.format;
import static java.lang.System.out;
import static moar.ansi.Ansi.GREEN;
import static moar.ansi.Ansi.GREEN_UNDERLINED;
import static moar.ansi.Ansi.PURPLE;
import static moar.ansi.Ansi.PURPLE_UNDERLINED;
import static moar.ansi.Ansi.RED;
import static moar.ansi.Ansi.RED_UNDERLINED;
import static moar.ansi.Ansi.cyanBold;
import static moar.ansi.Ansi.green;
import static moar.ansi.Ansi.purple;
import static moar.ansi.Ansi.red;
import static moar.sugar.MoarStringUtil.middleTruncate;
import static moar.sugar.Sugar.require;
import static moar.sugar.thread.MoarThreadSugar.$;
import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import moar.ansi.Ansi;
import moar.ansi.StatusLine;

public abstract class BaseStatusCommand
    extends
    BaseCommand {
  private final String currentPath = require(() -> new File(".").getCanonicalPath());

  protected void doStatus(String filter, Boolean detail) {
    var a = async;
    var f = futures;

    AtomicInteger maxLineLen = new AtomicInteger();

    var step1 = new Vector<Runnable>();
    var step2 = new Vector<Runnable>();
    var modules = getModules();
    var s = new StatusLine();
    s.setCount(getFilteredSize(filter), format("scan '%s'", filter));
    for (var module : modules) {
      String name = module.getName();
      boolean filterMatches = name.matches(filter);
      if (filterMatches) {
        var m = module;
        $(a, f, () -> {
          m.getBranch();
        });
        $(a, f, () -> {
          m.getUncommitedFiles().size();
        });
        $(a, f, () -> {
          m.getAheadCommits();
        });
        $(a, f, () -> {
          m.getBehindCommits();
        });
        $(a, f, () -> {
          m.getAheadOriginCommits();
        });
        $(a, f, () -> {
          m.getBehindOriginCommits();
        });
        $(a, f, () -> {
          m.getBehindMasterCommits();
        });
        $(f);
        s.completeOne();
        step1.add(() -> {
          Integer lineLen = getLineLen(module);
          maxLineLen.set(max(maxLineLen.get(), lineLen));
          step2.add(() -> {
            outLine(module, maxLineLen.get());
            if (detail) {
              outDetail(module);
            }
          });
        });
      }
    }
    s.setCount(step1.size(), "Sizing");
    for (var task : step1) {
      task.run();
      s.completeOne();
    }
    s.setCount(step2.size(), "Rendering");
    for (var task : step2) {
      task.run();
      s.completeOne();
    }
    s.remove();
  }

  private String getFormattedLine(MoarModule module, Boolean ansi, Integer padding) {
    Boolean priorEnabled = Ansi.enabled(ansi);
    try {
      var moduleNumber = module.getModuleNumber();
      var b = new StringBuilder(moduleNumber + ". ");
      String modulePath = require(() -> module.getDir().getCanonicalPath());
      if (modulePath.equals(currentPath)) {
        b.append(cyanBold(module.getName()));
      } else {
        b.append(module.getName());
      }
      b.append(" ");

      Integer uncommitedCount = module.getUncommitedFiles().size();
      if (uncommitedCount != 0) {
        b.append(purple(format("[%d]", uncommitedCount)));
        b.append(" ");
      }
      Integer aheadCount = module.getAheadCommits().size();
      Integer behindCount = module.getBehindCommits().size();
      if (aheadCount != 0 || behindCount != 0) {
        b.append("(");
        b.append(green(aheadCount));
        if (behindCount != 0) {
          b.append("/");
          b.append(red(behindCount));
        }
        b.append(") ");
      }
      b.append("-".repeat(padding) + "-> ");
      String upstream = module.getUpstreamBranch();
      upstream = upstream.replaceAll("^fork/", "");
      upstream = upstream.replaceAll("^feature/", "");
      b.append(upstream);
      return b.toString();
    } finally {
      Ansi.enabled(priorEnabled);
    }
  }

  private Integer getLineLen(MoarModule module) {
    return getFormattedLine(module, FALSE, 0).length();
  }

  private void outAheadDetail(String branch, MoarModule module) {
    int aheadCount = module.getAheadCommits().size();
    if (aheadCount > 0) {
      out.print("  ");
      out.print(format(GREEN.apply("(%d) "), aheadCount));
      out.println(GREEN_UNDERLINED.apply(format("HEAD -> %s", branch)));
      outDetailLines(out, GREEN, module.getAheadCommits());
    }
  }

  private void outAheadOriginDetail(String branch, MoarModule module) {
    int aheadOriginCount = module.getAheadOriginCommits().size();
    if (aheadOriginCount > 0) {
      out.print("  ");
      out.print(format(GREEN.apply("(%d) "), aheadOriginCount));
      out.println(GREEN_UNDERLINED.apply(format("%s -> origin/develop", branch)));
      outDetailLines(out, GREEN, module.getAheadOriginCommits());
    }
  }

  private void outBehindDetail(String branch, MoarModule module) {
    int behindCount = module.getBehindCommits().size();
    if (behindCount > 0) {
      out.print("  ");
      out.print(format(RED.apply("(%d) "), behindCount));
      out.println(RED_UNDERLINED.apply(format("HEAD <- %s", branch)));
      outDetailLines(out, RED, module.getBehindCommits());
    }
  }

  private void outBehindMasterDetail(String branch, MoarModule module) {
    int behindMasterCount = module.getBehindMasterCommits().size();
    if (behindMasterCount > 0) {
      out.print("  ");
      out.print(format(PURPLE.apply("[%d] "), behindMasterCount));
      out.println(PURPLE_UNDERLINED.apply(format("origin/develop <- origin/master", branch)));
      outDetailLines(out, PURPLE, module.getBehindMasterCommits());
    }
  }

  private void outBehindOriginDetail(String branch, MoarModule module) {
    int behindOriginCount = module.getBehindOriginCommits().size();
    if (behindOriginCount > 0) {
      out.print("  ");
      out.print(format(RED.apply("(%d) "), behindOriginCount));
      out.println(RED_UNDERLINED.apply(format("%s <- origin/develop", branch)));
      outDetailLines(out, RED, module.getBehindOriginCommits());
    }
  }

  private void outDetail(MoarModule module) {
    String branch = module.getUpstreamBranch();
    if (module.getUncommitedFiles().size() > 0) {
      outUncommitedDetail(module);
    }
    if (module.getAheadCommits().size() > 0) {
      outAheadDetail(branch, module);
    }
    if (module.getBehindCommits().size() > 0) {
      outBehindDetail(branch, module);
    }
    if (module.getAheadOriginCommits().size() > 0) {
      outAheadOriginDetail(branch, module);
    }
    if (module.getBehindOriginCommits().size() > 0) {
      outBehindOriginDetail(branch, module);
    }
    if (module.getBehindMasterCommits().size() > 0) {
      outBehindMasterDetail(branch, module);
    }
    out.println();
  }

  private void outDetailLines(PrintStream out, Ansi color, List<String> lines) {
    var i = 0;
    for (var line : lines) {
      var lineNumber = ++i;
      line = middleTruncate(line, 6, 80, "...");
      var formattedLine = format("%s - %s", format("%6d", lineNumber), line);
      out.println(color.apply(formattedLine));
    }
  }

  private void outLine(MoarModule module, Integer maxLineLen) {
    Integer lineLen = getLineLen(module);
    StringBuilder b = new StringBuilder();
    b.append(getFormattedLine(module, null, maxLineLen - lineLen));
    b.append(" ");
    Integer aheadOriginCount = module.getAheadOriginCommits().size();
    Integer behindOriginCount = module.getBehindOriginCommits().size();
    Integer behindMasterCount = module.getBehindMasterCommits().size();
    if (aheadOriginCount != 0 || behindOriginCount != 0) {
      b.append("(");
      b.append(green(aheadOriginCount));
      if (behindOriginCount != 0) {
        b.append("/");
        b.append(red(behindOriginCount));
      }
      b.append(") ");
    }
    if (behindMasterCount != 0) {
      b.append(purple(format("[%d]", behindMasterCount)));
    }
    out.println(b.toString());
  }

  private void outUncommitedDetail(MoarModule module) {
    int uncommitedCount = module.getUncommitedFiles().size();
    if (uncommitedCount > 0) {
      out.print("  ");
      out.print(PURPLE.apply(format("[%d]", uncommitedCount)));
      out.print(" ");
      out.println(PURPLE_UNDERLINED.apply("-> HEAD"));
      outDetailLines(out, PURPLE, module.getUncommitedFiles());
    }
  }
}