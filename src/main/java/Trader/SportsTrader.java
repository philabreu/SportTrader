package Trader;

import Bet.*;
import Bet.FootballBet.FootballBet;
import Bet.FootballBet.FootballBetGenerator;
import SiteConnectors.*;
import Sport.FootballMatch;
import com.google.gson.internal.LinkedTreeMap;
import org.apache.commons.logging.Log;
import org.json.simple.JSONObject;
import tools.MyLogHandler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;
import static tools.printer.*;

public class SportsTrader {

    private static final Logger log = Logger.getLogger(SportsTrader.class.getName());

    public int MAX_MATCHES;
    public int MIN_SITES_PER_MATCH;
    public boolean IN_PLAY;
    public int HOURS_AHEAD;
    public boolean CHECK_MARKETS;
    public boolean PLACE_BETS;
    public Map<String, Boolean> ACTIVE_SITES;


    public HashMap<String, Class> siteClasses;
    public HashMap<String, BettingSite> siteObjects;
    public int session_Update_interval = 4; // in hours

    public FootballBetGenerator footballBetGenerator;
    public ArrayList<EventTrader> eventTraders;


    public SportsTrader(){
        Thread.currentThread().setName("Main");
        log.setUseParentHandlers(false);
        log.setLevel(Level.INFO);
        try {
            log.addHandler(new MyLogHandler());
        } catch (Exception e) {
            e.printStackTrace();
            String msg = "Error Setting up logging. Exiting";
            print(msg);
            log.severe(msg);
            System.exit(1);
        }

        try {
            setupConfig("config.json");
        } catch (Exception e) {
            e.printStackTrace();
            log.severe("Invalid config file. Exiting...");
            System.exit(1);
        }

        siteClasses = new HashMap<String, Class>();
        siteClasses.put("betfair", Betfair.class);
        siteClasses.put("matchbook", Matchbook.class);
        siteClasses.put("smarkets", Smarkets.class);

        siteObjects = new HashMap<String, BettingSite>();
        eventTraders = new ArrayList<EventTrader>();
    }


    private void setupConfig(String config_filename) throws Exception {
        Map config = getJSONResource(config_filename);
        String[] required = new String[] {"MAX_MATCHES", "IN_PLAY", "HOURS_AHEAD", "CHECK_MARKETS",
                "PLACE_BETS", "ACTIVE_SITES", "MIN_SITES_PER_MATCH"};

        for (String field: required){
            if (!(config.keySet().contains(field))){
                String msg = String.format("Config file does not contain field '%s'", field);
                log.severe(msg);
                throw new Exception(msg);
            }
        }

        MAX_MATCHES = ((Double) config.get("MAX_MATCHES")).intValue();
        MIN_SITES_PER_MATCH = ((Double) config.get("MIN_SITES_PER_MATCH")).intValue();
        IN_PLAY = (boolean) config.get("IN_PLAY");
        HOURS_AHEAD = ((Double) config.get("HOURS_AHEAD")).intValue();
        CHECK_MARKETS = (boolean) config.get("CHECK_MARKETS");
        PLACE_BETS = (boolean) config.get("PLACE_BETS");
        ACTIVE_SITES = (Map<String, Boolean>) config.get("ACTIVE_SITES");

        config.remove("ACTIVE_SITES");
        log.info(String.format("Config:       %s", config.toString()));
        log.info(String.format("Site configs: %s", ACTIVE_SITES.toString()));
    }


    public void run(){
        log.info("Running SportsTrader.");

        // Run bet/taut generator
        footballBetGenerator = new FootballBetGenerator();


        // Initialize site object for each site class and add to map
        for (Map.Entry<String, Class> entry : siteClasses.entrySet() ) {
            String site_name = entry.getKey();
            Class site_class = entry.getValue();


            // Check site appears in config and is set to active
            if (!ACTIVE_SITES.containsKey(site_name)){
                log.severe("Site %s appears in class list but has no config entry. Skipping.");
                continue;
            }
            boolean site_active = ACTIVE_SITES.get(site_name);
            if (!site_active){
                log.info(String.format("Site %s not activated in config. Skipping.", site_name));
                continue;
            }

            // Initialize Site object
            try {
                //BettingSite site_obj = (BettingSite) site_class.getConstructor().newInstance();
                BettingSite site_obj =  (BettingSite) Class.forName(site_class.getName()).newInstance();
                siteObjects.put(site_name, site_obj);

            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                e.printStackTrace();
                log.severe(String.format("Error instantiating site object for %s", site_name));
                continue;
            }

            log.info(String.format("Successfully setup betting site connector for %s.", site_name));
        }


        // Exit if no sites have worked.
        if (siteObjects.size() <= 0){
            log.severe("None of the sites could be instantiated, finishing.");
            return;
        }


        // Collect initial football matches
        ArrayList<FootballMatch> footballMatches = null;
        try {
            footballMatches = getFootballMatches();
        } catch (IOException | URISyntaxException | InterruptedException e) {
            e.printStackTrace();
            log.severe("Error getting initial football matches. Exiting...");
            System.exit(1);
        }
        log.info(String.format("Found %d matches within given timeframe.", footballMatches.size()));



        for (FootballMatch footballMatch: footballMatches){
            log.info(String.format("Attempting to verify and setup %s.", footballMatch));

            // Attempt to link with Flashscores
            try{
                footballMatch = FlashScores.verifyMatch(footballMatch);
            } catch (InterruptedException | IOException | URISyntaxException | FlashScores.verificationException e) {
                log.warning(String.format("Failed to link match %s to flashscores - %s", footballMatch, e.getMessage()));
                continue;
            }

            // Attempt to setup site event trackers for all sites for this match
            EventTrader eventTrader = new EventTrader(footballMatch, siteObjects, footballBetGenerator);
            int successfull_site_connections = eventTrader.setupMatch();
            if (successfull_site_connections < MIN_SITES_PER_MATCH){
                log.warning(String.format("Only %d/%d sites connected for %s. MIN_SITES_PER_MATCH=%d.",
                        successfull_site_connections, siteObjects.size(), footballMatch, MIN_SITES_PER_MATCH));
                continue;
            }

            eventTraders.add(eventTrader);
            if (eventTraders.size() >= MAX_MATCHES){
                break;
            }
        }


        log.info(String.format("%d matches setup successfully with at least %d site connectors.",
                eventTraders.size(), MIN_SITES_PER_MATCH));

        // Exit if none have worked.
        if (eventTraders.size() == 0){
            log.severe("0 matches have been setup correctly. Exiting.");
            System.exit(0);
        }


        // End if config says so.
        if (!CHECK_MARKETS){
            log.info("CHECK_MARKETS set to false. Ending here.");
            System.exit(0);
        }



        // Run all event traders
        for (EventTrader eventTrader: eventTraders){
            eventTrader.thread = new Thread(eventTrader);
            eventTrader.thread.setName("ET: " + eventTrader.match.name);
            eventTrader.thread.start();
        }
        log.info("All Event Traders started.");

        // Start the session updater to keep all sites connected.
        SessionsUpdater sessionsUpdater = new SessionsUpdater();
        Thread sessionUpdaterThread = new Thread(sessionsUpdater);
        sessionUpdaterThread.setName("Session Updater");
        sessionUpdaterThread.start();


    }


    public class SessionsUpdater implements Runnable {

        @Override
        public void run() {
            log.info("Session updater started.");

            while (true){
                try {
                    // Sleep for required amount of hours between updates
                    sleep(1000*60*60*session_Update_interval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                for (Map.Entry<String, BettingSite> entry: siteObjects.entrySet()){
                    String site_name = entry.getKey();
                    BettingSite bet_site = entry.getValue();

                    try {
                        bet_site.login();
                        log.info(String.format("Successfully refreshed session for %s", site_name));
                    } catch (CertificateException | UnrecoverableKeyException | NoSuchAlgorithmException
                            | KeyStoreException | KeyManagementException | IOException | URISyntaxException e) {

                        e.printStackTrace();

                        String msg = String.format("Error while trying to refresh session for %s.", site_name);
                        log.severe(msg);
                    }

                }
            }

        }
    }


    private ArrayList<FootballMatch> getFootballMatches() throws IOException, URISyntaxException,
            InterruptedException {

        String site_name = "matchbook";
        BettingSite site = siteObjects.get(site_name);
        Instant from;
        Instant until;
        Instant now = Instant.now();

        // Find time frame to search in
        if (IN_PLAY){
            from = now.minus(5, ChronoUnit.HOURS);
            log.info(String.format("Searching for matches from %s, %d hours ahead and in-play.",
                    site_name, HOURS_AHEAD));
        } else{
            from = now;
            log.info(String.format("Searching for matches from %s, %d hours ahead.",
                    site_name, HOURS_AHEAD));
        }
        until = now.plus(HOURS_AHEAD, ChronoUnit.HOURS);

        // Get all football matches found in time frame.
        ArrayList<FootballMatch> fms = site.getFootballMatches(from, until);

        return fms;
     }


    public static void main(String[] args){
        SportsTrader st = new SportsTrader();
        st.run();
    }

}
