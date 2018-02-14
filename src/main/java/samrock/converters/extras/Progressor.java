package samrock.converters.extras;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.WindowListener;

import sam.console.ansi.ANSI;
import sam.swing.progress.MyProgressMonitor;

public class Progressor {

    private final MyProgressMonitor progress;
    private final boolean disabled;

    /**
     * create disabled Progressor 
     */
    public Progressor() {
        progress = null;
        disabled = true;
    }
    public Progressor(String title, int max) {
        progress = new MyProgressMonitor("title", 0, max) ; 
        progress.setVisible(true);
        progress.setExitOnClose(true);
        progress.setReset("Waiting");

        disabled = false;
    }
    public boolean isDisabled() {
        return disabled;
    }
    public void addWindowListener(WindowListener action) {
        if(disabled) return;
        progress.addWindowListener(action);
    }
    public void dispose() {
        if(disabled) return;
        progress.dispose();
    }
    public int getCurrentProgress() {
        if(disabled) return 0;
        return progress.getCurrentProgress();
    }
    public void increaseBy1() {
        if(disabled) return;
        progress.increaseBy1();
    }
    public void resetProgress() {
        if(disabled) return;
        progress.resetProgress();
    }
    public void setBackground(Color color) {
        if(disabled) return;
        progress.setBackground(color);
    }
    public void setCompleted() {
        if(disabled) return;
        progress.setCompleted();
    }
    public void setContentPane(Component component) {
        if(disabled) return;
        progress.setContentPane(component);
    }
    public void setExitOnClose(boolean b) {
        if(disabled) return;
        progress.setExitOnClose(b);
    }
    public void setFailed() {
        if(disabled) return;
        progress.setFailed();
    }
    public void setForeground(Color color) {
        if(disabled) return;
        progress.setForeground(color);
    }
    public void setForeground(Font font) {
        if(disabled) return;
        progress.setForeground(font);
    }
    public void setLocation(int x, int y) {
        if(disabled) return;
        progress.setLocation(x, y);
    }
    public void setLocationRelativeTo(Component c) {
        if(disabled) return;
        progress.setLocationRelativeTo(c);
    }
    public void setMaximum(int max) {
        if(disabled) return;
        progress.setMaximum(max);
    }
    public void setMinimum(int min) {
        if(disabled) return;
        progress.setMinimum(min);
    }
    public void setProgress(int value) {
        if(disabled) return;
        progress.setProgress(value);
    }
    public void setReset(String title) {
        if(disabled) {
            Utils.println(ANSI.yellow(title));
            return;
        }
        progress.setReset(title);
    }
    public void setShowRemainingAsTitle(boolean showRemainingAsTitle) {
        if(disabled) return;
        progress.setShowRemainingAsTitle(showRemainingAsTitle);
    }
    public void setString(String text) {
        if(disabled) {
            Utils.println(ANSI.yellow(text));
            return;
        }
        progress.setString(text);
    }
    public void setTitle(String title) {
        if(disabled) {
            Utils.println(ANSI.yellow(title));
            return;
        }
        progress.setTitle(title);
    }
    public void setVisible(boolean b) {
        if(disabled) return;
        progress.setVisible(b);
    }
}
