// Tapatsawin Chaieua          6813105
// Thanapon Thanadunpremdet    6813106
// Tanawat Wongwad             6813107
// Natthanon Achachan          6813241
package Project3_6813107;

import static Project3_6813107.GameRegression.check;
import static Project3_6813107.GameRegression.read;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

final class SwingRegression {
    public static void main(String[] args) throws Exception {
        MainApplication.main(new String[0]);
        MainApplication launcher = awaitWindow(MainApplication.class);
        try {
            onSwing(() -> configureLauncher(launcher));
            GameFrame game = awaitWindow(GameFrame.class);
            GamePanel panel = (GamePanel) read(game, "gamePanel");
            Thread gameThread = (Thread) read(panel, "gameThread");
            Thread spawner = (Thread) read(panel, "waveSpawnerThread");
            check(gameThread.isAlive() && spawner.isAlive(), "Both simulation threads must be running");
            onSwing(() -> {
                checkSize(game);
                check(panel.isInSafeCamp(), "Start button opens the game in Safe Camp");
                check(panel.getPlayer().getPlayerName().equals("Regression") && panel.getPlayer().getPlayerClass()
                        == GameData.PlayerClass.ENGINEER, "Player settings must carry over to the game");
                ((JButton) read(game, "pauseButton")).doClick();
                check(panel.isPaused(), "HUD pause button pauses");
                return null;
            });
            verifyWorkshop(game, panel, true);
            onSwing(() -> {
                ((JButton) read(game, "pauseButton")).doClick();
                check(!panel.isPaused(), "HUD pause button resumes");
                return null;
            });
            verifyWorkshop(game, panel, false);
            verifyFieldMerchant(panel);
            verifyPerkChoice(game, panel);
            onSwing(() -> {
                game.dispatchEvent(new WindowEvent(game, WindowEvent.WINDOW_CLOSING));
                check(launcher.isShowing(), "Closing the game restores the launcher");
                return null;
            });
            check(!gameThread.isAlive() && !spawner.isAlive(), "Closing stops both simulation threads");
            System.out.println("Swing: controls, window sizes, workshop craft, focus, pause/resume, merchant, perk choice and thread shutdown OK");
        } finally {
            onSwing(() -> {
                ((Timer) read(launcher, "animationTimer")).stop();
                for (Window window : Window.getWindows()) {
                    if (window instanceof GameFrame game && window.isDisplayable()) {
                        ((GamePanel) read(game, "gamePanel")).stopGame();
                    }
                    window.dispose();
                }
                AssetLoader.stopMusic();
                return null;
            });
        }
    }

    private static Void configureLauncher(MainApplication launcher) throws Exception {
        checkSize(launcher);
        JRadioButton[] classes = (JRadioButton[]) read(launcher, "classRadios");
        JComboBox<?> regions = (JComboBox<?>) read(launcher, "mapCombo");
        check(classes.length >= 5 && regions.getItemCount() >= 5, "Launcher has enough class and region choices");
        for (String name : new String[]{"autoFireBox", "doubleSpawnBox", "hitboxBox", "soundBox", "musicBox"}) {
            check(read(launcher, name) instanceof JCheckBox, "Each option has a Swing checkbox");
        }

        ((JCheckBox) read(launcher, "soundBox")).doClick();
        ((JCheckBox) read(launcher, "musicBox")).doClick();
        ((JSlider) read(launcher, "volumeSlider")).setValue(41);
        check(AssetLoader.getVolume() == 41, "Volume slider must update the sound system");
        classes[GameData.PlayerClass.ENGINEER.ordinal()].doClick();
        regions.setSelectedIndex(GameData.MapType.GAS_STATION.ordinal());
        ((JSlider) read(launcher, "difficultySlider")).setValue(2);
        JTextField name = (JTextField) read(launcher, "nameField");
        JButton start = (JButton) read(launcher, "startButton");
        name.setText(" ");
        start.doClick();
        check(launcher.isShowing(), "Invalid name must keep the launcher open");
        name.setText(" Regression ");
        start.doClick();
        return null;
    }

    private static void verifyWorkshop(GameFrame game, GamePanel panel, boolean initiallyPaused) throws Exception {
        sendKey(panel, KeyEvent.VK_B);
        UpgradeDialog workshop = awaitWindow(UpgradeDialog.class);
        onSwing(() -> {
            checkSize(workshop);
            check(panel.isPaused(), "Workshop pauses gameplay");
            JTable recipes = (JTable) read(workshop, "recipeTable");
            check(recipes.getRowCount() == GameData.RECIPES.size() && recipes.getColumnCount() == 3,
                    "Workshop JTable presents all recipes");
            if (initiallyPaused) {
                Player player = panel.getPlayer();
                GameData.UpgradeRecipe pistol = GameData.RECIPES.stream()
                        .filter(recipe -> recipe.weapon == GameData.WeaponType.PISTOL)
                        .findFirst().orElseThrow();
                player.getCost(pistol).forEach(player::addItem);
                int row = GameData.RECIPES.indexOf(pistol);
                recipes.setRowSelectionInterval(row, row);
                workshop.refreshTable();
                ((JButton) read(workshop, "craftButton")).doClick();
                check(player.getEquippedPrimaryGun() == GameData.WeaponType.PISTOL, "Workshop button crafts Pistol");
            }
            ((JButton) read(workshop, "closeButton")).doClick();
            return null;
        });
        onSwing(() -> {
            check(game.isShowing() && panel.isPaused() == initiallyPaused, "Workshop restores the preceding pause state");
            return null;
        });
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (onSwing(() -> game.getMostRecentFocusOwner() == panel)) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Keyboard focus did not return to the game panel");
    }

    private static void verifyPerkChoice(GameFrame game, GamePanel panel) throws Exception {
        var perk = GameData.TacticalPerk.HIGH_CALIBER;
        SwingUtilities.invokeLater(() -> game.showPerkSelectionDialog(List.of(
                perk, GameData.TacticalPerk.RAPID_TRIGGER, GameData.TacticalPerk.TRAP_MASTER)));
        Dialog dialog = awaitWindow(Dialog.class);
        onSwing(() -> {
            checkSize(dialog);
            check(panel.isPaused(), "Perk selection pauses gameplay");
            JButton choose = findButton(dialog, "CHOOSE");
            check(choose != null, "Perk dialog has a choice button");
            choose.doClick();
            return null;
        });
        onSwing(() -> {
            check(!panel.isPaused() && panel.getPlayer().getPerkLevel(perk) == 1,
                    "Choosing a perk applies it and resumes the game");
            return null;
        });
    }

    private static JButton findButton(Container parent, String label) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton button && label.equals(button.getText())) return button;
            if (component instanceof Container container) {
                JButton found = findButton(container, label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void verifyFieldMerchant(GamePanel panel) throws Exception {
        onSwing(() -> {
            Point gate = ((GameMap) read(panel, "map")).getGatePosition();
            panel.getPlayer().setPosition(gate.x, gate.y);
            return null;
        });
        sendKey(panel, KeyEvent.VK_F);
        long deadline = System.nanoTime() + 4_000_000_000L;
        while (panel.isInSafeCamp() && System.nanoTime() < deadline) Thread.sleep(20);
        check(panel.getChamber() == 1, "F opens Chamber 1 from the hub");
        sendKey(panel, KeyEvent.VK_B);
        Dialog shop = awaitWindow(Dialog.class);
        onSwing(() -> {
            checkSize(shop);
            check(shop.getTitle().contains("Field Merchant") && panel.isPaused(), "B opens the paused field shop");
            shop.dispose();
            return null;
        });
        onSwing(() -> {
            check(!panel.isPaused(), "Game resumes after closing the merchant");
            return null;
        });
    }

    private static void sendKey(GamePanel panel, int code) throws Exception {
        onSwing(() -> {
            panel.keyPressed(new KeyEvent(panel, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED));
            return null;
        });
    }

    private static void checkSize(Window window) {
        check(window.getWidth() <= 1200 && window.getHeight() <= 700, "Window dimensions: " + window.getSize());
    }

    private static <T extends Window> T awaitWindow(Class<T> type) throws Exception {
        long deadline = System.nanoTime() + 8_000_000_000L;
        do {
            T match = onSwing(() -> {
                for (Window window : Window.getWindows()) {
                    if (type.isInstance(window) && window.isShowing()) return type.cast(window);
                }
                return null;
            });
            if (match != null) return match;
            Thread.sleep(30);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Window did not open: " + type.getSimpleName());
    }

    private static <T> T onSwing(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
