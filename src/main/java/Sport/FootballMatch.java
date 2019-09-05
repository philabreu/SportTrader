package Sport;

import Bet.Bet;
import SiteConnectors.Betfair;
import org.apache.commons.codec.binary.StringUtils;

import java.text.Normalizer;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static tools.printer.print;

public class FootballMatch extends Match{

    public static String[] removable_keywords = {"fc", "town", "city", "women", "w", "of", "and", "&"};

    public Team team_a;
    public Team team_b;

    public FootballMatch(Instant START, Team TEAM_A, Team TEAM_B){
        super();
        start_time = START;
        team_a = TEAM_A;
        team_b = TEAM_B;
        name = team_a.name + " v " + team_b.name;
    }

    public static FootballMatch parse(String start, String name) throws ParseException {
        Instant start_time = Instant.parse(start);

        String[] teams = name.trim().split("\\sv\\s|\\sV\\s|\\svs\\s|\\sVS\\s|\\sVs\\s");
        if (teams.length != 2){
            String msg = String.format("Cannot parse Match name '%s'", name);
            throw new ParseException(msg, 1);
        }
        return new FootballMatch(start_time, new Team(teams[0]), new Team(teams[1]));
    }

    public String toString(){
        return "[" + name + " @ " + start_time.toString() + "]";
    }


    public boolean same_match(FootballMatch match, Betfair betfair){
        log.fine(String.format("Checking match for %s and %s.", this, match));

        // Check Start time, false if different
        if (!start_time.equals(match.start_time)){
            return false;
        }

        // Check FS IDs (if one doesnt have one then this will be false)
        // true if the same
        if (this.FSID == null || this.FSID.equals(match.FSID)){
            return true;
        }

        // No match
        log.fine(String.format("No match for %s and %s.", this, match));
        return false;
    }

    public static boolean same_team(String T1, String T2){
        return same_team(T1, T2, true);
    }

    public static boolean same_team(String T1, String T2, boolean deep_check){
        //log.fine(String.format("Checking teams match for %s and %s.", T1, T2));

        // Check exact strings
        if (T1.equals(T2)){
            //log.fine(String.format("Match found for teams '%s' & '%s'. Exact.", T1, T2));
            return true;
        }

        // Normalise strings. Lowercase, replace punctuation, normalise accented chars
        String t1 = Normalizer.normalize(T1.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{P}", "");
        String t2 = Normalizer.normalize(T2.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{P}", "");

        // Check normalised strings
        if (t1.equals(t2)){
            //log.fine(String.format("Match found for teams '%s' & '%s'. Normalised '%s' & '%s'.", T1, T2, t1, t2));
            return true;
        }

        // Check strings are the same without whitespace (as a list of words)
        ArrayList<String> p1 = new ArrayList<String>(Arrays.asList(t1.split("\\s+")));
        ArrayList<String> p2 = new ArrayList<String>(Arrays.asList(t2.split("\\s+")));
        if (p1.equals(p2)){
            //log.fine(String.format("Match found for teams '%s' & '%s'. Same words %s %s.", T1, T2, p1, p2));
            return true;
        }

        // Check if words all appear even if out of order (as a set)
        HashSet<String> s1 = new HashSet<String>(p1);
        HashSet<String> s2 = new HashSet<String>(p2);
        if (s1.equals(s2)){
            //log.fine(String.format("Match found for teams '%s' & '%s'. Mixed order %s %s.", T1, T2, s1, s2));
            return true;
        }

        // Only check further if triggered to. This stops recurred calls from going further.
        if (!deep_check){
            return false;
        }


        // Removes keywords such as FC from name to see if match occurs.
        for (String keyword: removable_keywords){
            if (s1.contains(keyword) || s2.contains(keyword)){
                p1.remove(keyword);
                p2.remove(keyword);

                boolean success = same_team(String.join(" ", p1), String.join(" ", p2), false);
                if (success){
                    //log.fine(String.format("Match found for teams once '%s' removed. '%s' & '%s'.", keyword, T1, T2));
                    return true;
                }
            }
        }


        log.fine(String.format("No match found for %s and %s.", T1, T2));
        return false;
    }

    public boolean inList(List<FootballMatch> list){
        return list_contains(list, this);
    }

    public static boolean list_contains(List<FootballMatch> list, Match match){
        for (FootballMatch match2: list){
            if (match.FSID.equals(match2.FSID)){
                return true;
            }
        }
        return false;
    }

}
