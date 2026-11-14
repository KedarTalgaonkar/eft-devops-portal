package com.euronet.eft.portal.ai.digest;

import com.euronet.eft.portal.ai.digest.ExecutiveDigest.Action;
import com.euronet.eft.portal.ai.digest.ExecutiveDigest.AreaNote;
import com.euronet.eft.portal.ai.digest.ExecutiveDigest.Rag;
import com.euronet.eft.portal.ai.digest.ExecutiveDigest.Risk;
import com.euronet.eft.portal.ai.digest.ExecutiveDigest.Severity;
import com.euronet.eft.portal.dashboard.DashboardDtos.AttentionItem;
import com.euronet.eft.portal.pipelines.InsightDtos.Kpi;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Mock mode: writes the digest from the facts with fixed sentence templates (no AI involved). */
final class MockDigestWriter {

    /** Indian English number formatting (1,00,000). */
    private static final Locale INDIA = Locale.of("en", "IN");

    private MockDigestWriter() {
    }

    static ExecutiveDigest write(DigestFacts f) {
        Rag overall = Rag.valueOf(f.overall().name());
        DigestFacts.Delivery d = f.delivery();
        DigestFacts.Estate e = f.estate();

        // the status chip next to the headline already says "On track" / "Needs attention" / "Action required"
        String headline = switch (overall) {
            case GREEN -> pct(d.successRatePercent().current()) + " build success and all key services up.";
            case AMBER -> plural(d.failingPipelines(), "pipeline") + " failing and " + plural(e.servicesDown(), "service") + " down.";
            case RED -> plural(d.failingPipelines(), "failing pipeline") + " and " + plural(f.totalIssues(), "open issue") + " need action.";
        };

        List<String> sentences = new ArrayList<>();
        sentences.add("Over the last " + f.windowDays() + " days " + fmt(d.totalBuilds().current()) + " builds ran across "
                + d.pipelines() + " pipelines with " + pct(d.successRatePercent().current()) + " success"
                + change(d.successRatePercent(), "points", true) + ".");
        sentences.add("Teams deployed " + fmt1(d.deploymentsPerDay().current()) + " times a day"
                + change(d.deploymentsPerDay(), "", true) + ", and failed pipelines took "
                + fmt1(d.meanTimeToRecoverHours().current()) + " hours on average to go green again.");
        sentences.add(e.servicesDown() == 0 ? "All probed services are up."
                : plural(e.servicesDown(), "deployed service") + (e.servicesDown() == 1 ? " is" : " are") + " not responding.");
        if (e.firingAlerts() > 0 || e.failingQualityGates() > 0) {
            sentences.add(plural(e.firingAlerts(), "monitoring alert") + (e.firingAlerts() == 1 ? " is" : " are") + " firing and "
                    + plural(e.failingQualityGates(), "project") + (e.failingQualityGates() == 1 ? " fails" : " fail")
                    + " the quality gate.");
        }

        List<String> improvements = new ArrayList<>();
        improved(improvements, "Build success", d.successRatePercent(), true, "%");
        improved(improvements, "Failure rate", d.failureRatePercent(), false, "%");
        improved(improvements, "Deployments per day", d.deploymentsPerDay(), true, "");
        improved(improvements, "Time to recover", d.meanTimeToRecoverHours(), false, " h");
        improved(improvements, "Average build time", d.averageBuildMinutes(), false, " min");

        List<Risk> risks = f.topIssues().stream().limit(5)
                .map(i -> new Risk(i.title(), i.area(), Severity.valueOf(i.severity().name()), i.detail()))
                .toList();
        List<Action> actions = f.topIssues().stream().limit(5).map(MockDigestWriter::action).toList();
        List<AreaNote> areas = f.areas().stream()
                .map(a -> new AreaNote(a.label(), Rag.valueOf(a.status().name()), a.headline() + " (" + a.metric() + ")"))
                .toList();
        return new ExecutiveDigest(headline, overall, String.join(" ", sentences), improvements, risks, actions, areas);
    }

    private static Action action(AttentionItem i) {
        String verb = switch (i.area().toLowerCase(Locale.ROOT)) {
            case "delivery" -> "Fix";
            case "security" -> "Patch";
            case "code quality" -> "Bring back to green:";
            default -> "Investigate";
        };
        String owner = i.owner() == null || i.owner().isBlank() ? "Unassigned" : i.owner();
        return new Action(verb + " " + i.title(), owner, i.area());
    }

    private static void improved(List<String> out, String label, Kpi k, boolean higherIsBetter, String unit) {
        if (k == null || k.current() == null || k.previous() == null || k.current().equals(k.previous())) {
            return;
        }
        boolean better = higherIsBetter ? k.current() > k.previous() : k.current() < k.previous();
        if (better && out.size() < 4) {
            out.add(label + " improved from " + fmt1(k.previous()) + unit + " to " + fmt1(k.current()) + unit + ".");
        }
    }

    private static String change(Kpi k, String unit, boolean higherIsBetter) {
        if (k == null || k.current() == null || k.previous() == null) {
            return "";
        }
        double diff = k.current() - k.previous();
        if (Math.abs(diff) < 0.05) {
            return " (unchanged)";
        }
        String dir = diff > 0 ? "up " : "down ";
        return " (" + dir + fmt1(Math.abs(diff)) + (unit.isEmpty() ? "" : " " + unit) + " on the previous period)";
    }

    private static String plural(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private static String pct(Double v) {
        return v == null ? "no" : fmt1(v) + "%";
    }

    private static String fmt(Double v) {
        return v == null ? "0" : java.text.NumberFormat.getIntegerInstance(INDIA).format(Math.round(v));
    }

    private static String fmt1(Double v) {
        return v == null ? "n/a" : String.format(Locale.ROOT, "%.1f", v);
    }
}
