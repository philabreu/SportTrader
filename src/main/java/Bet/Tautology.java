package Bet;

import Bet.Bet;
import org.json.simple.JSONArray;

import java.util.ArrayList;
import java.util.Collection;

public class Tautology {

    public ArrayList<Bet> bets;

    public Tautology(ArrayList<Bet> bets){
        this.bets = bets;
        this.bets.trimToSize();
    }

    public int size(){
        return bets.size();
    }

    public JSONArray toJSON(boolean full){
        JSONArray ja = new JSONArray();
        for (Bet bet: bets){
            if (full){
                ja.add(bet.json());
            }
            else{
                ja.add(bet.id());
            }
        }
        return ja;
    }

    public String toString(){
        return toJSON(false).toString();
    }
}
