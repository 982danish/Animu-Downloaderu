package com.codingotaku.apps;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.codingotaku.apis.animecrawler.Anime;
import com.codingotaku.apis.animecrawler.AnimeCrawlerAPI;
import com.codingotaku.apis.animecrawler.AnimeList;
import com.codingotaku.apis.animecrawler.EpisodeList;
import com.codingotaku.apis.animecrawler.Result;
import com.codingotaku.apps.callback.Crawler;
import com.codingotaku.apps.callback.NotificationListener;
import com.codingotaku.apps.custom.AlertDialog;
import com.codingotaku.apps.custom.LoadDialog;
import com.codingotaku.apps.custom.Message;
import com.codingotaku.apps.custom.NotificationController;
import com.codingotaku.apps.custom.NotificationController.Type;
import com.codingotaku.apps.source.AnimeSources;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public class MainFXMLController implements Crawler, NotificationListener {
	private static Logger logger = Logger.getLogger(MainFXMLController.class.getName());
	@FXML
	private VBox root; // Root

	// Anime download and interactions
	@FXML
	private TextField search;
	@FXML
	private ComboBox<String> sources;
	@FXML
	private ListView<HBox> notificationList;
	// Anime information
	@FXML
	private ImageView poster;
	@FXML
	private TextArea area;
	@FXML
	private Button addBookmark;
	// For displaying downloads
	@FXML
	private ScrollPane scrollPane;
	@FXML
	private DownloadController downloadController;
	@FXML
	private EpisodeController episodeController;
	@FXML
	private BookmarkController bookmarkController;

	private final ObservableList<HBox> notifications = FXCollections.observableArrayList();
	private final Map<NotificationController,HBox> notControllerMapper = new HashMap<>();
	private final ObservableList<Anime> animes = FXCollections.observableArrayList();

	private final AnimeCrawlerAPI api = new AnimeCrawlerAPI();
	private final AnimeSources animeProviders = AnimeSources.getInstance();

	private ListView<Anime> animeList;

	private Window window;
	private Stage stage;

	private VBox animeBox;

	private Image defaultImg;
	private Message message;

	private boolean isInitialized = false;

	public void loadAnime(Window window) {
		var source = animeProviders.values().get(sources.getSelectionModel().getSelectedItem());

		animeList.getSelectionModel().clearSelection();
		message.setMessage("Anime List", "Loading List..", false);
		LoadDialog.showDialog(window, "Loading...", message);
		api.listAllAnime(source, this::loadedAnime);
	}

	@FXML
	private void initialize() {
		animeProviders.values().keySet().forEach(sources.getItems()::add);
		message = new Message();
		animeBox = (VBox) scrollPane.getContent().lookup("#list");
		defaultImg = new Image(getClass().getResourceAsStream("/icons/panda1.jpg"));
		animeList = new ListView<>();

		sources.getSelectionModel().select(0);
		poster.setImage(defaultImg);
		notificationList.setItems(notifications);
		initListeners();
		isInitialized = true;
	}

	@FXML
	private void reload() {
		if (window == null)
			window = root.getScene().getWindow();
		loadAnime(window);
		poster.setImage(defaultImg);
		area.clear();
		addBookmark.setDisable(true);
	}

	private void initListeners() {
		// Bind notification listeners
		downloadController.setNotificationListener(this);
		bookmarkController.setNotificationListener(this);
		episodeController.setNotificationListener(this);
		bookmarkController.loadBookmarks(this.downloadController);
		// Search
		search.textProperty().addListener((observable, oldValue, newValue) -> search(newValue));

		// Select Anime
		animeList.getSelectionModel().selectedItemProperty().addListener((observable, oldV, newV) -> {
			if (newV != null) {
				if (window == null)
					window = root.getScene().getWindow();
				message.setMessage("Synopsis", "Loading...", false);
				message.setMessage("Poster", "Fetching...", false);
				message.setMessage("Episodes", "Listing...", false);

				LoadDialog.showDialog(window, "Loading Anime...", message);
				new Thread(() -> api.getSynopsys(newV, this::loadedSynopsis)).start();
				new Thread(() -> api.listAllEpisodes(newV, this::loadedEpisodes)).start();
				new Thread(() -> api.getPosterUrl(newV, this::loadedPoster)).start();
			}
		});
		
		// make notification list not select
		notificationList.getSelectionModel().selectedItemProperty().addListener((selected, old, current)->{
			if(!notificationList.getSelectionModel().isEmpty())
			 Platform.runLater(()->notificationList.getSelectionModel().clearSelection());
		});
	}

	private void search(String text) {
		animeList.getSelectionModel().clearSelection();
		if (!(animes.isEmpty())) {
			if (text.isEmpty()) {
				animeList.setItems(animes);
			} else {
				animeList
						.setItems(animes.filtered(label -> label.getName().toLowerCase().contains(text.toLowerCase())));
			}
		}
		if (!animeBox.getChildren().contains(animeList)) {
			animeBox.getChildren().setAll(animeList);
		}
	}

	@Override
	public void loadedSynopsis(String content, Result result) {
		Platform.runLater(() -> {
			String text;
			String msg = result.getStatus().name();
			episodeController.setDownloadController(downloadController);
			// Synopsis can be empty
			if (content != null && content.length() > 0) {
				String anime = animeList.getSelectionModel().getSelectedItem().getName();
				boolean disable = bookmarkController.getAnimeList().stream()
						.filter(item -> item.getName().equals(anime)).count() == 1;
				addBookmark.setDisable(disable);
				text = "Anime : " + anime + "\n" + content.replaceAll("([\\w]+ :)", "\n$1").trim();
			} else {
				text = "Unable to load";
				if (result.getStatus() == Result.Status.ERROR) {
					text += " : " + result.getError().getMessage();
				}
				msg = text;
			}
			message.setMessage("Synopsis", msg, true);
			area.setText(text);
			area.setEditable(false);
			area.setWrapText(true);
		});
	}

	@Override
	public void loadedPoster(String url, Result result) {
		Platform.runLater(() -> {
			String msg = result.getStatus().name();
			if (url == null) {
				poster.setImage(defaultImg);
				if (result.getError() != null) {
					msg = "Error : " + result.getError().getMessage();
				}
			} else {
				poster.setImage(new Image(url));
			}
			message.setMessage("Poster", msg, true);
		});
	}

	@Override
	public void loadedAnime(AnimeList animesTmp, Result result) {
		Platform.runLater(() -> {
			message.setMessage("Anime List", result.getStatus().name(), true);
			if (result.getStatus() == Result.Status.OK) {

				animes.setAll(animesTmp.getAnimes());
				search(search.getText());
				VBox.setVgrow(animeList, Priority.ALWAYS);
				animeBox.getChildren().setAll(animeList);
			} else {

				var dialog = new AlertDialog("Error", "Error loading anime, " + result.getError().getMessage());
				if (stage == null)
					stage = (Stage) root.getScene().getWindow(); // an ugly way of initializing stage
				// Calculate the center position of the parent Stage
				double centerXPosition = stage.getX() + stage.getWidth() / 2d;
				double centerYPosition = stage.getY() + stage.getHeight() / 2d;

				dialog.setOnShowing(e -> {
					dialog.setX(centerXPosition - dialog.getDialogPane().getWidth() / 2d);
					dialog.setY(centerYPosition - dialog.getDialogPane().getHeight() / 2d);
				});
				dialog.showAndWait();
			}
		});

	}

	@Override
	public void loadedEpisodes(EpisodeList episodesTmp, Result result) {
		String episodes = "Episodes";
		if (result.getStatus() == Result.Status.OK) {
			message.setMessage(episodes, result.getStatus().name(), true);
			episodeController.loadEpisodes(episodesTmp);
		} else {
			Platform.runLater(() -> {
				message.setMessage(episodes, "Error : " + result.getError().getMessage(), true);
				episodeController.loadEpisodes(new EpisodeList(0, 0, new ArrayList<>())); // Load an empty list if error
				var dialog = new AlertDialog("Error", "Error loading anime, " + result.getError().getMessage());
				if (stage == null)
					stage = (Stage) root.getScene().getWindow(); // an ugly way of initializing stage
				// Calculate the center position of the parent Stage
				double centerXPosition = stage.getX() + stage.getWidth() / 2d;
				double centerYPosition = stage.getY() + stage.getHeight() / 2d;

				dialog.setOnShowing(e -> {
					dialog.setX(centerXPosition - dialog.getDialogPane().getWidth() / 2d);
					dialog.setY(centerYPosition - dialog.getDialogPane().getHeight() / 2d);
				});
				dialog.showAndWait();
			});
		}
	}

	@FXML
	private void loadBookmarks(Event event) {
		Tab tab = (Tab) event.getTarget();
		if (tab.getId().equals("bookmarkTab") && tab.isSelected() && isInitialized) {
			bookmarkController.loadBookmarks(this.downloadController);
		}
	}

	@FXML
	private void addBookmark() {
		ObjectMapper mapper = new ObjectMapper();
		Anime anime = animeList.getSelectionModel().getSelectedItem();
		String name = anime.getName().replaceAll("[^a-zA-Z0-9 \\.\\-]", "_");
		name = "anime/" + name + ".json";
		File file = new File("anime");
		if (!file.exists()) {
			file.mkdirs();
		}
		try {
			FileOutputStream out = new FileOutputStream(name, true);
			mapper.writeValue(out, anime);
			addBookmark.setDisable(true);
		} catch (IOException e) {
			logger.severe(e.getMessage());
		}

	}

	@Override
	public void created(Type type, String message) {
		FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/Notification.fxml"));
		try {
			HBox box = fxmlLoader.load();
			NotificationController controller = fxmlLoader.getController();
			controller.init(message, type, this);
			Platform.runLater(() -> {
				notControllerMapper.put(controller, box);
				notifications.add(box);	
			});
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe(e.getMessage());
		}
	}

	@Override
	public void closed(NotificationController notification) {
		Platform.runLater(()-> notifications.remove(notControllerMapper.get(notification)));
	}
}