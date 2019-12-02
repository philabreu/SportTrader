package Trader;

import Bet.Bet;
import Bet.MarketOddsReport;
import SiteConnectors.RequestHandler;
import SiteConnectors.SiteEventTracker;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

public class MarketOddsReportWorker implements Runnable {

    public static final Logger log = Logger.getLogger(SportsTrader.class.getName());

    public Thread thread;
    public BlockingQueue<RequestHandler> queue;
    public boolean waiting;

    private boolean exit_flag;


    public MarketOddsReportWorker(BlockingQueue<RequestHandler> queue){
        waiting = false;
        exit_flag = false;
        this.queue = queue;
        thread = new Thread(this);
    }


    public void safe_exit(){
        exit_flag = true;
        thread.interrupt();
    }


    public void start(){
        thread.start();
    }


    public void interrupt(){
        thread.interrupt();
    }


    public boolean isWaiting(){
        return waiting == true;
    }


    @Override
    public void run() {

        RequestHandler requestHandler = null;

        while (!exit_flag){
            try{

                // Wait for a job from the queue
                waiting = true;
                requestHandler = null;
                requestHandler = queue.take();
                waiting = false;

                // Unpack Site Event Tracker and bets from request.
                Object[] arguments = (Object[]) requestHandler.request;
                SiteEventTracker siteEventTracker = (SiteEventTracker) arguments[0];
                Collection<Bet> bets = (Collection<Bet>) arguments[1];


                // If bets null, return error mor and finish loop
                if (bets == null){
                    String error = String.format("Bets passed into MOR is null.");
                    log.severe(error);
                    requestHandler.setResponse(MarketOddsReport.ERROR(error));
                    continue;
                }
                if (requestHandler.isCancelled()){
                    continue;
                }

                MarketOddsReport mor = siteEventTracker.getMarketOddsReport(bets);

                // Apply mor to request handler
                requestHandler.setResponse(mor);

            }
            catch (InterruptedException e){
                waiting = false;
                log.fine(String.format("MOR worker interuppted"));
                if (requestHandler != null){
                    requestHandler.setResponse(MarketOddsReport.ERROR(
                            String.format("Interuppted Exception occured in MOR worker loop.")));
                }
            }
            catch (Exception e){
                waiting = false;
                log.severe("Exception %s in market odds report worker");
                e.printStackTrace();
                if (requestHandler != null){
                    requestHandler.setResponse(MarketOddsReport.ERROR(
                            String.format("General exception in MOR worker loop. %s", e)));
                }
            }
        }

        log.info(String.format("Ending MarketOddsReport worker."));
    }
}
