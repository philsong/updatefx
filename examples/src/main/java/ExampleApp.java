import com.google.common.util.concurrent.Uninterruptibles;
import com.vinumeris.updatefx.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.bouncycastle.math.ec.ECPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;

public class ExampleApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(ExampleApp.class);
    public static int VERSION = 1;

    public static void main(String[] args) throws IOException {
        // We want to store updates in our app dir so must init that here.
        AppDirectory.initAppDir("UpdateFX Example App");
        setupLogging();
        // re-enter at realMain, but possibly running a newer version of the software i.e. after this point the
        // rest of this code may be ignored.
        UpdateFX.bootstrap(ExampleApp.class, AppDirectory.dir(), args);
    }

    public static void realMain(String[] args) {
        launch(args);
    }

    private static java.util.logging.Logger logger;
    private static void setupLogging() throws IOException {
        logger = java.util.logging.Logger.getLogger("");
        logger.getHandlers()[0].setFormatter(new BriefLogFormatter());
        FileHandler handler = new FileHandler(AppDirectory.dir().resolve("log").toString(), true);
        handler.setFormatter(new BriefLogFormatter());
        logger.addHandler(handler);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
        Thread.currentThread().setContextClassLoader(ExampleApp.class.getClassLoader());
        // Must be done twice for the times when we come here via realMain.
        AppDirectory.initAppDir("UpdateFX Example App");

        // We can return from here to get restarted to a newer version, but normally we don't want that.
        log.info("Hello World! This is version " + VERSION);

        ProgressIndicator indicator = showGiantProgressWheel(primaryStage);

        List<ECPoint> pubkeys = Crypto.decode("03BAB59EBCF0943981B2AA4EC05FA87915386BB59D60E67C5370977AED00D9E0DF");
        Updater updater = new Updater("http://localhost:8000/", "ExampleApp/" + VERSION, VERSION,
                AppDirectory.dir(), UpdateFX.findCodePath(ExampleApp.class),
                pubkeys, 1) {
            @Override
            protected void updateProgress(long workDone, long max) {
                super.updateProgress(workDone, max);
                // Give UI a chance to show.
                Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            }
        };

        indicator.progressProperty().bind(updater.progressProperty());

        log.info("Checking for updates!");
        updater.setOnSucceeded(event -> {
            try {
                UpdateSummary summary = updater.get();
                if (summary.newVersion > VERSION) {
                    log.info("Restarting to get version " + summary.newVersion);
                    UpdateFX.restartApp();
                }
            } catch (Throwable e) {
                log.error("oops", e);
            }
        });

        indicator.setOnMouseClicked(ev -> UpdateFX.restartApp());

        new Thread(updater, "UpdateFX Thread").start();

        primaryStage.show();
    }

    private ProgressIndicator showGiantProgressWheel(Stage stage) {
        ProgressIndicator indicator = new ProgressIndicator();
        BorderPane borderPane = new BorderPane(indicator);
        borderPane.setMinWidth(640);
        borderPane.setMinHeight(480);
        Label label = new Label("V" + VERSION);
        borderPane.setTop(label);
        Scene scene = new Scene(borderPane);
        stage.setScene(scene);
        return indicator;
    }
}
