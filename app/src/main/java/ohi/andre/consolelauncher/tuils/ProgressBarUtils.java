package ohi.andre.consolelauncher.tuils;

import ohi.andre.consolelauncher.managers.VariablesManager;

public class ProgressBarUtils {

    public static String getFilledChar() {
        String val = VariablesManager.getVariable("progress_bar_filled");
        return (val != null && !val.isEmpty()) ? val : "=";
    }

    public static String getHeadChar() {
        String val = VariablesManager.getVariable("progress_bar_head");
        return (val != null && !val.isEmpty()) ? val : ">";
    }

    public static String getEmptyChar() {
        String val = VariablesManager.getVariable("progress_bar_empty");
        return (val != null && !val.isEmpty()) ? val : " ";
    }

    public static String getLeftChar() {
        String val = VariablesManager.getVariable("progress_bar_left");
        return (val != null && !val.isEmpty()) ? val : "[";
    }

    public static String getRightChar() {
        String val = VariablesManager.getVariable("progress_bar_right");
        return (val != null && !val.isEmpty()) ? val : "]";
    }

    public static String renderBar(int percentage, int barLength) {
        if (percentage < 0) percentage = 0;
        if (percentage > 100) percentage = 100;

        String filled = getFilledChar();
        String head = getHeadChar();
        String empty = getEmptyChar();
        String left = getLeftChar();
        String right = getRightChar();

        int filledCount = (percentage * barLength) / 100;
        StringBuilder sb = new StringBuilder();
        sb.append(left);

        for (int i = 0; i < barLength; i++) {
            if (i < filledCount - 1) {
                sb.append(filled);
            } else if (i == filledCount - 1) {
                if (filledCount == barLength) {
                    sb.append(filled);
                } else {
                    sb.append(head);
                }
            } else {
                sb.append(empty);
            }
        }
        sb.append(right);
        return sb.toString();
    }

    public static String formatSpeed(double bytesPerSec) {
        if (bytesPerSec <= 0) return "0 B/s";
        if (bytesPerSec < 1024) return String.format("%.0f B/s", bytesPerSec);
        if (bytesPerSec < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSec / 1024.0);
        return String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0));
    }

    public static String buildProgressDisplay(String pkgName, int currentPkgIndex, int totalPkgs, int fileProgress, double bytesPerSec) {
        int overallPercentage = totalPkgs > 0 ? (((currentPkgIndex - 1) * 100) + fileProgress) / totalPkgs : fileProgress;
        if (overallPercentage > 100) overallPercentage = 100;

        String fileBar = renderBar(fileProgress, 18);
        String overallBar = renderBar(overallPercentage, 18);
        String speedStr = formatSpeed(bytesPerSec);

        StringBuilder sb = new StringBuilder();
        sb.append("Installing: ").append(pkgName);
        if (totalPkgs > 1) {
            sb.append(" (").append(currentPkgIndex).append("/").append(totalPkgs).append(")");
        }
        sb.append("\nFile:    ").append(fileBar).append(" ").append(fileProgress).append("%  ").append(speedStr);
        sb.append("\nOverall: ").append(overallBar).append(" ").append(overallPercentage).append("%");
        return sb.toString();
    }
}
