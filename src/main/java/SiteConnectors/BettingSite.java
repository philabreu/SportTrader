package SiteConnectors;

import Bet.Bet;
import Bet.Bet.BetType;
import Bet.BetOffer;
import Bet.BetOrder;
import Bet.PlacedBet;
import Sport.FootballMatch;
import Trader.SportsTrader;
import org.json.simple.parser.ParseException;
import tools.Requester;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public abstract class BettingSite {

    public static Logger log = Logger.getLogger(SportsTrader.class.getName());

    //public final static String name = "ABSTRACT_BETTING_SITE";
    public String ssldir;
    public Requester requester;

    public boolean exit_flag;

    public BigDecimal balance;
    public BigDecimal exposure;

    public Lock balanceLock = new ReentrantLock();
    public BigDecimal balance_buffer = new BigDecimal("10.00");

    public BettingSite() {
        exit_flag = false;

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            ssldir = "C:/ssl/";
        } else { // Assume linux
            ssldir = System.getProperty("user.home") + "/ssl/";
        }

        balance = new BigDecimal("0.00");
    }


    public abstract void login() throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException,
            KeyStoreException, KeyManagementException, IOException, URISyntaxException, InterruptedException, ParseException;


    public abstract String getSessionToken() throws IOException, CertificateException, NoSuchAlgorithmException,
            KeyStoreException, KeyManagementException, UnrecoverableKeyException, URISyntaxException, ParseException;


    public abstract BigDecimal winCommissionRate();

    public abstract BigDecimal lossCommissionRate();


    public abstract String getName();


    public abstract String getID();


    public abstract BigDecimal minBackersStake();


    public abstract BigDecimal minLayersStake(BigDecimal odds);


    public static Set<String> getIDs(Collection<BettingSite> sites){
        Set<String> ids = new HashSet<>();
        for (BettingSite site: sites){
            ids.add(site.getID());
        }
        return ids;
    }


    public BigDecimal getBalance() {
        balanceLock.lock();
        BigDecimal b = balance;
        balanceLock.unlock();
        return b;
    }


    public void setBalance(BigDecimal new_balance){
        balanceLock.lock();
        balance = new_balance;
        balanceLock.unlock();
    }


    public abstract void safe_exit();


    public abstract void updateAccountInfo() throws InterruptedException, IOException, URISyntaxException;


    public boolean useBalance(BigDecimal amount) {
        // A request for using the balance, removes balance and returns true if successful

        balanceLock.lock();
        boolean result = false;
        BigDecimal new_balance = balance.subtract(amount);

        if (new_balance.compareTo(balance_buffer) == 1) {
            result = true;
            balance = new_balance;
        } else {
            result = false;
            log.warning(String.format("Request to use %s of %s balance failed as this would take it below the buffer of %s.",
                    amount.toString(), this.getName(), balance_buffer.toString()));
        }

        balanceLock.unlock();
        return result;
    }


    public abstract SiteEventTracker getEventTracker();



    public BigDecimal stakePartOfInvestment(BigDecimal investment) {

        // 100% of stake + 1% loss commission is the total investment so a total 1.01 ratio for 1%
        BigDecimal total_ratio = BigDecimal.ONE.add(lossCommissionRate());

        // The amount of the total investment which is the stake (1.00 out of 1.01)
        BigDecimal stake_ratio = BigDecimal.ONE.divide(total_ratio, 12, RoundingMode.HALF_UP);

        // Multiply total investment by the amount of it that is stake, to get the total stake
        BigDecimal stake_amount = investment.multiply(stake_ratio);

        return stake_amount;
    }


    public BigDecimal investmentNeededForStake(BigDecimal stake) {

        // Total amount of commission charged if lost bet
        BigDecimal loss_commission_amount = stake.multiply(lossCommissionRate());

        // Sum up the total amount of money needed
        BigDecimal total_inv_needed = stake.add(loss_commission_amount);

        return total_inv_needed;
    }



    public abstract List<FootballMatch> getFootballMatches(Instant from, Instant until)
            throws IOException, URISyntaxException, InterruptedException;


    public List<FootballMatch> getFootballMatches() throws InterruptedException, IOException, URISyntaxException {
        return getFootballMatches(Instant.now(), Instant.now().plus(24, ChronoUnit.HOURS));
    }



    public PlacedBet placeBet(BetOrder betOrder, BigDecimal MIN_ODDS_RATIO) throws IOException, URISyntaxException {
        List<BetOrder> betOrders = new ArrayList<>();
        betOrders.add(betOrder);
        List<PlacedBet> placedBets = placeBets(betOrders, MIN_ODDS_RATIO);
        if (placedBets.size() != 1){
            log.severe(String.format("SENT 1 BETORDER BUT GOT BACK %s PLACED BETS", placedBets.size()));
        }
        return placedBets.get(0);
    }


    public abstract List<PlacedBet> placeBets(List<BetOrder> betOrders, BigDecimal MIN_ODDS_RATIO)
            throws IOException, URISyntaxException;



    public BigDecimal ROI(BetOffer bet_offer, BigDecimal investment, boolean real) {
        // Default ROI, commission on profits only

        return ROI(bet_offer.bet.getType(), bet_offer.odds, bet_offer.site.winCommissionRate(), investment, real);
    }


    public static BigDecimal ROI(BetType betType, BigDecimal odds, BigDecimal commission_rate, BigDecimal investment,
                                 boolean real) {
        // Default ROI, commission on profits only

        BigDecimal roi;

        // BACK
        if (betType == BetType.BACK) {
            BigDecimal backers_stake = investment;
            BigDecimal backers_profit = Bet.backStake2LayStake(backers_stake, odds);
            BigDecimal commission = backers_profit.multiply(commission_rate);
            roi = backers_stake.add(backers_profit).subtract(commission);
        }

        // LAY
        else {
            BigDecimal layers_stake = investment;
            BigDecimal layers_profit = Bet.layStake2backStake(layers_stake, odds);
            BigDecimal commission = layers_profit.multiply(commission_rate);
            roi = layers_stake.add(layers_profit).subtract(commission);
        }

        // Round to nearest penny if 'real' value;
        if (real) {
            roi = roi.setScale(2, RoundingMode.HALF_UP);
        }

        return roi;
    }


    @Override
    public boolean equals(Object o){
        return ((o instanceof BettingSite) && getName().equals(((BettingSite) o).getName()));
    }

    @Override
    public String toString(){
        return getName();
    }


    public static BigDecimal round(BigDecimal value, BigDecimal increment, RoundingMode roundingMode) {
        if (increment.signum() == 0) {
            // 0 increment does not make much sense, but prevent division by 0
            return value;
        } else {
            BigDecimal divided = value.divide(increment, 0, roundingMode);
            BigDecimal result = divided.multiply(increment);
            return result;
        }
    }



    public static void main(String[] args) {

    }
}
