package com.dakusuta.tools.anime.source;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;

import com.dakusuta.tools.anime.custom.AnimeLabel;
import com.dakusuta.tools.anime.custom.EpisodeLabel;

import javafx.scene.image.Image;

public abstract class IServer {
	protected String path = null;
	protected Document selectedDoc = null;
	protected List<AnimeLabel> animeList = new ArrayList<>();
	protected List<EpisodeLabel> episodes = new ArrayList<>();

	protected String getPath() {
		return path;
	}

	public abstract List<AnimeLabel> loadAnime(Document doc);

	public abstract void getSynopsys(AnimeLabel label);

	public abstract List<EpisodeLabel> loadEpisodes();

	abstract Image getPoster(String imgUrl);
}