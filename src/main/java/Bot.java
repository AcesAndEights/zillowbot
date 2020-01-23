import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static data.Credentials.botApiToken;
import static data.Credentials.botUserName;
import static helpers.CheckRegexp.checkPrice;
import static helpers.OkHttpClient.defaultHttpClient;
import static helpers.StoreValues.storeValues;
import static java.util.Objects.requireNonNull;

public class Bot extends TelegramLongPollingBot {

    public static void main(String[] args) {
        ApiContextInitializer.init();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            telegramBotsApi.registerBot(new Bot());
        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return botUserName;
    }

    @Override
    public String getBotToken() {
        return botApiToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        sendStartMsg(update, update.getMessage().getChatId().toString(), "How do you want to find a flat?");
        areaSearchStatenIslandMessage(update, update.getMessage().getChatId().toString());
        areaSearch(update, update.getMessage().getChatId().toString());
        byZipCodeStep(update, update.getMessage().getChatId().toString());
    }

    //Message after /start
    private synchronized void sendStartMsg(Update update, String chatId, String messageText) {
        if (update.getMessage().getText().equals("/start")) {
            storeValues("start value", update.getMessage().getText());
            SendMessage message = new SendMessage()
                    .setChatId(chatId)
                    .setText(messageText);
            ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> keyboard = new ArrayList<>();
            KeyboardRow row = new KeyboardRow();
            row.add("Staten Island");
            row.add("By ZIP Code");
            keyboard.add(row);
            replyKeyboardMarkup.setKeyboard(keyboard);
            message.setReplyMarkup(replyKeyboardMarkup);
            try {
                execute(message);

            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    //Zip code search preparations
    private synchronized void byZipCodeStep (Update update, String chatId) {
        if (update.getMessage().getText().equals("By ZIP Code")) {
            SendMessage message = new SendMessage()
                    .setChatId(chatId)
                    .setText("Sorry, it doesn't work now");
            ReplyKeyboardRemove keyboardMarkup = new ReplyKeyboardRemove();
            message.setReplyMarkup(keyboardMarkup);
            try {
                execute(message);

            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    //Zip code search
//    private synchronized void zipCodeSearch(Update update, String chatId) throws IOException {
//        if (checkZip(update.getMessage().getText())) {
//            storeValues("zip", update.getMessage().getText());
//            SendMessage message = new SendMessage()
//                    .setChatId(chatId)
//                    .setText("Ok, Search started");
//            schedulerUpdateSourceFlats("1600");
//            schedulerForNewFlats("1600", chatId);
//            try {
//                execute(message);
//
//            } catch (TelegramApiException e) {
//                e.printStackTrace();
//            }
//
//        }
//    }

    //Message after setting up area search for getting price
    private synchronized void areaSearchStatenIslandMessage (Update update, String chatId) {
        if (update.getMessage().getText().equals("Staten Island")) {
            SendMessage message = new SendMessage()
                    .setChatId(chatId)
                    .setText("Enter MAX price for flat with $ in the end. Example: 1300$");
            ReplyKeyboardRemove keyboardMarkup = new ReplyKeyboardRemove();
            message.setReplyMarkup(keyboardMarkup);
            try {
                execute(message);

            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    //Address Search start message by price
    private synchronized void areaSearch (Update update, String chatId) {
        if (checkPrice(update.getMessage().getText())) {
            SendMessage message = new SendMessage()
                    .setChatId(chatId)
                    .setText("Ok, Search started");
            String price = update.getMessage().getText().replaceAll("\\$+", "");
            ReplyKeyboardRemove keyboardMarkup = new ReplyKeyboardRemove();
            message.setReplyMarkup(keyboardMarkup);
            try {
                execute(message);

            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            schedulerUpdateSourceFlats(price);
            schedulerForNewFlats(price, chatId);
        }
    }

    //Scheduler for new flats checker every 12 minutes, with delay of 10 sec
    private void schedulerForNewFlats(final String price, String chatId) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    findNewFlats(price, chatId);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 10000, 720000);
    }

    //Scheduler for new source flats (for future comparing) once in a half hour
    private void schedulerUpdateSourceFlats(final String price) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    storeOriginalFlatsList(price);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 1800000);
    }

    private List<String> sourceMap = new ArrayList<>();
    private List<String> newMap = new ArrayList<>();

    //Getting actual flat list by Area (Staten Island NY)
    private JSONArray getActualFlatsByAreaList(String price) throws IOException {
        Request request = new Request.Builder()
                .url("https://www.zillow.com/search/GetSearchPageState.htm?searchQueryState=%7B%22pagination%22%3A%7B%7D%2C%22usersSearchTerm%22%3A%22Staten%20Island%2C%20New%20York%2C%20NY%22%2C%22mapBounds%22%3A%7B%22west%22%3A-74.2466236435547%2C%22east%22%3A-74.06122935644532%2C%22south%22%3A40.44399909526452%2C%22north%22%3A40.701129685866846%7D%2C%22regionSelection%22%3A%5B%7B%22regionId%22%3A27252%2C%22regionType%22%3A17%7D%5D%2C%22isMapVisible%22%3Atrue%2C%22mapZoom%22%3A12%2C%22filterState%22%3A%7B%22isForSaleByAgent%22%3A%7B%22value%22%3Afalse%7D%2C%22isForSaleByOwner%22%3A%7B%22value%22%3Afalse%7D%2C%22isNewConstruction%22%3A%7B%22value%22%3Afalse%7D%2C%22isForSaleForeclosure%22%3A%7B%22value%22%3Afalse%7D%2C%22isComingSoon%22%3A%7B%22value%22%3Afalse%7D%2C%22isAuction%22%3A%7B%22value%22%3Afalse%7D%2C%22isPreMarketForeclosure%22%3A%7B%22value%22%3Afalse%7D%2C%22isPreMarketPreForeclosure%22%3A%7B%22value%22%3Afalse%7D%2C%22isForRent%22%3A%7B%22value%22%3Atrue%7D%2C%22monthlyPayment%22%3A%7B%22max%22%3A" + price + "%7D%2C%22price%22%3A%7B%22max%22%3A405711%7D%7D%2C%22isListVisible%22%3Atrue%7D")
                .addHeader("referer", "https://www.zillow.com/homes/Staten-Island,-New-York,-NY_rb/")
                .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.79 Safari/537.36")
                .get()
                .build();

        Response response = defaultHttpClient().newCall(request).execute();
        JSONObject responseJson = new JSONObject(requireNonNull(response.body() != null ? response.body().string() : null));

        return responseJson.getJSONObject("searchResults").getJSONArray("mapResults");
    }

    //Save source list of flats
    private void storeOriginalFlatsList(String price) throws IOException {

        JSONArray searchResults = getActualFlatsByAreaList(price);

        for (int i = 0; i < searchResults.length(); i++) {
            sourceMap.add("https://www.zillow.com" + searchResults.getJSONObject(i).get("detailUrl").toString());
        }

        System.out.println(sourceMap.size());

        System.out.println("source list");
        sourceMap.forEach(System.out::println);
    }

    //Get new list of flats
    private void storeNewFlatsList(String price) throws IOException {

        JSONArray searchResults = getActualFlatsByAreaList(price);

        for (int i = 0; i < searchResults.length(); i++) {
            newMap.add("https://www.zillow.com" + searchResults.getJSONObject(i).get("detailUrl").toString());
        }

        System.out.println("new List");
        newMap.forEach(System.out::println);
    }

    //Getting actual info, comparing
    private void findNewFlats(String price, String chatId) throws IOException {
        storeNewFlatsList(price);
        List <String> addedKeys = newMap;
        addedKeys.removeAll(sourceMap);

        if (addedKeys.size() > 0)
        {
            for (String addedKey : addedKeys) {
                SendMessage message = new SendMessage()
                        .setChatId(chatId)
                        .setText("Find out a new flat! \n" + addedKey);
                try {
                    execute(message);

                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}