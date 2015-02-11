package com.cardshifter.gdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.cardshifter.api.config.DeckConfig;
import com.cardshifter.api.outgoing.CardInfoMessage;
import com.cardshifter.gdx.Callback;
import com.cardshifter.gdx.CardshifterGame;
import com.cardshifter.gdx.TargetStatus;
import com.cardshifter.gdx.TargetableCallback;
import com.cardshifter.gdx.ui.CardshifterClientContext;
import com.cardshifter.gdx.ui.EntityView;
import com.cardshifter.gdx.ui.cards.CardViewSmall;

import java.util.*;

/**
 * Created by Simon on 2/10/2015.
 */
public class DeckBuilderScreen implements Screen, TargetableCallback {

    private static final int ROWS_PER_PAGE = 3;
    private static final int CARDS_PER_ROW = 4;
    private static final int CARDS_PER_PAGE = CARDS_PER_ROW * ROWS_PER_PAGE;

    private final Callback<DeckConfig> callback;
    private final Table table;
    private final CardshifterGame game;
    private final java.util.List<CardInfoMessage> cards;
    private final DeckConfig config;
    private final int pageCount;
    private final Table cardsTable;
    private final CardshifterClientContext context;
    private final Map<Integer, Label> countLabels = new HashMap<Integer, Label>();
    private final List<String> cardsInDeckList;
    private final List<String> savedDecks;
    private int page;
    private String deckName = "unnamed";
    private FileHandle external;

    public DeckBuilderScreen(CardshifterGame game, String modName, int gameId, final DeckConfig deckConfig, final Callback<DeckConfig> callback) {
        this.config = deckConfig;
        this.callback = callback;
        this.table = new Table(game.skin);
        this.table.setFillParent(true);
        this.game = game;
        this.context = new CardshifterClientContext(game.skin, gameId, null, game.stage);

        Map<Integer, CardInfoMessage> data = deckConfig.getCardData();
        cards = new ArrayList<CardInfoMessage>(data.values());
        Collections.sort(cards, new Comparator<CardInfoMessage>() {
            @Override
            public int compare(CardInfoMessage o1, CardInfoMessage o2) {
                return Integer.compare(o1.getId(), o2.getId());
            }
        });
        pageCount = (int) Math.ceil(cards.size() / CARDS_PER_PAGE);
        cardsTable = new Table(game.skin);
        cardsTable.defaults().space(4);
        TextButton doneButton = new TextButton("Done", game.skin);
        doneButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (config.total() >= config.getMinSize() && config.total() <= config.getMaxSize()) {
                    callback.callback(deckConfig);
                }
            }
        });

        table.add(cardsTable);
        cardsInDeckList = new List<String>(game.skin);
        table.add(cardsInDeckList);
        savedDecks = new List<String>(game.skin);
        savedDecks.addListener(new ActorGestureListener(){
            @Override
            public boolean longPress(Actor actor, float x, float y) {
                loadDeck(savedDecks.getSelected());
                return true;
            }
        });
        table.add(savedDecks).row();

        HorizontalGroup buttons = new HorizontalGroup();
        addPageButton(buttons, "Previous", -1, game.skin);
        buttons.addActor(doneButton);
        addPageButton(buttons, "Next", 1, game.skin);

        scanSavedDecks(game, table, savedDecks, modName);

        table.add(buttons, savedDecks);

        displayPage(1);
    }

    private void scanSavedDecks(final CardshifterGame game, Table table, List<String> savedDecks, String modName) {
        if (Gdx.files.isExternalStorageAvailable()) {
            external = Gdx.files.external("Cardshifter/decks/" + modName + "/");
            external.mkdirs();

            if (!external.exists()) {
                Gdx.app.log("Files", external.path() + " does not exist.");
                return;
            }

            java.util.List<String> list = new ArrayList<String>();
            for (FileHandle handle : external.list()) {
                if (!handle.isDirectory()) {
                    list.add(handle.nameWithoutExtension());
                }
            }
            savedDecks.setItems(list.toArray(new String[list.size()]));


            TextButton save = new TextButton("Save", game.skin);
            save.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    final TextField textField = new TextField(deckName, game.skin);
                    Dialog dialog = new Dialog("Deck Name", game.skin) {
                        @Override
                        protected void result(Object object) {
                            boolean result = (Boolean) object;
                            if (!result) {
                                return;
                            }
                            deckName = textField.getText();
                            saveDeck(deckName);
                        }
                    };
                    dialog.add(textField);
                    dialog.button("Save", true);
                    dialog.button("Cancel", false);
                    dialog.show(game.stage);
                }
            });
            table.addActor(save);
        }
    }

    private void saveDeck(String deckName) {
        FileHandle handle = external.child(deckName + ".deck");
        StringBuilder str = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : config.getChosen().entrySet()) {
            int count = entry.getValue();
            int id = entry.getKey();
            for (int i = 0; i < count; i++) {
                if (str.length() > 0) {
                    str.append(',');
                }
                str.append(id);
            }
        }
        String deckString = str.toString();
        savedDecks.getItems().add(deckName);
        handle.writeString(deckString, false);
    }

    private void loadDeck(String deckName) {
        FileHandle handle = external.child(deckName + ".deck");
        String deckString = handle.readString();
        config.clearChosen();
        for (String id : deckString.split(",")) {
            config.add(Integer.parseInt(id));
        }
        for (Map.Entry<Integer, Label> ee : countLabels.entrySet()) {
            ee.getValue().setText(countText(ee.getKey()));
        }
    }

    private String countText(int id) {
        Integer value = config.getChosen().get(id);
        return countText(id, value == null ? 0 : value);
    }

    private String countText(int id, int count) {
        int max = config.getMaxFor(id);
        return count + "/" + max;
    }

    private void addPageButton(Group table, String text, final int i, Skin skin) {
        TextButton button = new TextButton(text, skin);
        button.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                displayPage(page + i);
            }
        });
        table.addActor(button);
    }

    private void displayPage(int page) {
        this.page = page;
        countLabels.clear();
        int startIndex = (page - 1) * CARDS_PER_PAGE;
        cardsTable.clearChildren();
        for (int i = startIndex; i < startIndex + CARDS_PER_PAGE; i++) {
            if (cards.size() <= i) {
                break;
            }
            if (i % CARDS_PER_ROW == 0) {
                cardsTable.row();
            }
            CardInfoMessage card = cards.get(i);
            VerticalGroup choosableGroup = new VerticalGroup();
            CardViewSmall cardView = new CardViewSmall(context, card);
            cardView.setTargetable(TargetStatus.TARGETABLE, this);
            choosableGroup.addActor(cardView.getTable());
            Integer chosen = config.getChosen().get(card.getId());
            if (chosen == null) {
                chosen = 0;
            }
            Label label = new Label(chosen + "/" + config.getMaxFor(card.getId()), game.skin);
            countLabels.put(card.getId(), label);
            choosableGroup.addActor(label);
            cardsTable.add(choosableGroup);
        }
    }

    @Override
    public void render(float delta) {

    }

    @Override
    public void resize(int width, int height) {

    }

    @Override
    public void show() {
        game.stage.addActor(table);
    }

    @Override
    public void hide() {
        table.remove();
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void dispose() {

    }

    @Override
    public boolean addEntity(EntityView view) {
        final int id = view.getId();
        int max = config.getMaxFor(id);
        Integer chosen = config.getChosen().get(id);
        if (chosen == null) {
            chosen = 0;
        }
        int newChosen = (chosen + 1) % (max + 1);
        config.setChosen(id, newChosen);
        countLabels.get(id).setText(newChosen + "/" + max);
        return true;
    }
}
