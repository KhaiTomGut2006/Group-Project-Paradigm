package Project3_6813107;

import java.awt.*;
import javax.swing.*;

/** Score table with data, and the end-of-run summary dialog. Runs in a temp folder. */
public class UiShots2 {
    public static void main(String[] a) throws Exception {
        UiShots.out = new java.io.File(a[0]);
        String[] names = {"Alex", "Mint", "Boss Hunter", "Ploy", "Tee", "Ken"};
        String[] maps = {"Gas Station", "Survivor Camp", "Abandoned Bunker", "Wasteland Highway", "Quarantine Zone", "Gas Station"};
        for (int i = 0; i < names.length; i++) {
            GameData.ScoreEntry e = new GameData.ScoreEntry();
            e.name = names[i]; e.playerClass = GameData.PlayerClass.values()[i % 5].displayName; e.map = maps[i];
            e.score = 22000 - i * 3100; e.kills = 800 - i * 90; e.wave = 10 - i; e.seconds = 300 - i * 31;
            GameData.saveScore(e);
        }
        final MainApplication[] app = new MainApplication[1];
        UiShots.onEdt(() -> { Theme.installLookAndFeel(); app[0] = new MainApplication(); app[0].setVisible(true); });
        Thread.sleep(600);
        UiShots.onEdt(() -> UiShots.find(app[0].getContentPane(), JToggleButton.class, "SCORES").doClick());
        Thread.sleep(300);
        UiShots.onEdt(() -> { try { UiShots.shot(app[0].getContentPane(), "launcher_scores_filled"); } catch (Exception e) { throw new RuntimeException(e); } });
        SwingUtilities.invokeLater(() -> Theme.ask(app[0], Theme.Panel.METAL, "titles/survived", "Survived",
                "The horde is gone and the camp still stands. Your score is saved in the high-score table.",
                new String[][]{{"Score", "15794"}, {"Kills", "1022"}, {"Wave reached", "10"}, {"Time survived", "5:00"}, {"Map", "GAS STATION"}},
                "Continue", null));
        Thread.sleep(800);
        UiShots.onEdt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing()) {
                    try { UiShots.shot(((JDialog) w).getContentPane(), "summary_dialog"); } catch (Exception e) { throw new RuntimeException(e); }
                    w.dispose();
                }
            }
        });
        SwingUtilities.invokeLater(() -> System.out.println("quit answer=" + Theme.ask(app[0], Theme.Panel.DANGER, null, "QUIT THIS RUN?",
                "Your score from this run will not be saved.", null, "Quit", "Keep playing")));
        Thread.sleep(800);
        UiShots.onEdt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing()) {
                    try { UiShots.shot(((JDialog) w).getContentPane(), "quit_dialog"); } catch (Exception e) { throw new RuntimeException(e); }
                    w.dispose();
                }
            }
        });
        Thread.sleep(300);
        System.exit(0);
    }
}
