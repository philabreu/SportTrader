package Sport;

import org.apache.commons.codec.binary.StringUtils;

import java.text.Normalizer;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class FootballMatch extends Match{

    public String team_a;
    public String team_b;

    public FootballMatch(Instant START, String NAME) throws Exception {
        super();

        start_time = START;
        name = NAME;

        String[] parts = name.toLowerCase().split(" v ");
        if (parts.length != 2){
            parts = name.toLowerCase().split(" vs ");
            if (parts.length != 2){
                throw new Exception(String.format("Cannot find teams from name '%s'", name));
            }
        }

        team_a = parts[0];
        team_b = parts[1];
    }


    public FootballMatch(Instant START, String TEAM_A, String TEAM_B){
        start_time = START;
        team_a = TEAM_A;
        team_b = TEAM_B;
        name = team_a + " v " + team_b;
    }

    public static FootballMatch parse(String start, String name) throws ParseException {
        Instant start_time = Instant.parse(start);

        String[] teams = name.trim().split("\\sv\\s|\\sV\\s|\\svs\\s|\\sVS\\s|\\sVs\\s");
        if (teams.length != 2){
            String msg = String.format("Cannot parse Match name '%s'", name);
            log.warning(msg);
            throw new ParseException(msg, 1);
        }
        return new FootballMatch(start_time, teams[0], teams[1]);
    }

    public String toString(){
        return "[" + name + " @ " + start_time.toString() + "]";
    }


    public boolean same_match(FootballMatch match){
        log.fine(String.format("Checking match for %s and %s.", this, match));

        if (start_time.equals(match.start_time)
                && same_team(team_a, match.team_a)
                && same_team(team_b, match.team_b)){

            log.info(String.format("Match found for %s and %s.", this, match));
            return true;
        }

        log.fine(String.format("No match for %s and %s.", this, match));
        return false;

    }

    public static boolean same_team(String T1, String T2){
        return same_team(T1, T2, true);
    }

    public static boolean same_team(String T1, String T2, boolean deep_check){
        log.fine(String.format("Checking teams match for %s and %s.", T1, T2));

        // Check exact strings
        if (T1.equals(T2)){
            log.fine(String.format("Match found for teams '%s' & '%s'. Exact.", T1, T2));
            return true;
        }

        // Normalise strings. Lowercase, replace punctuation, normalise accented chars
        String t1 = Normalizer.normalize(T1.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{P}", "");
        String t2 = Normalizer.normalize(T2.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{P}", "");

        // Check normalised strings
        if (t1.equals(t2)){
            log.fine(String.format("Match found for teams '%s' & '%s'. Normalised '%s' & '%s'.", T1, T2, t1, t2));
            return true;
        }

        // Check strings are the same without whitespace (as a list of words)
        ArrayList<String> p1 = new ArrayList<String>(Arrays.asList(t1.split("\\s+")));
        ArrayList<String> p2 = new ArrayList<String>(Arrays.asList(t2.split("\\s+")));
        if (p1.equals(p2)){
            log.fine(String.format("Match found for teams '%s' & '%s'. Same words %s %s.", T1, T2, p1, p2));
            return true;
        }

        // Check if words all appear even if out of order (as a set)
        HashSet<String> s1 = new HashSet<String>(p1);
        HashSet<String> s2 = new HashSet<String>(p2);
        if (s1.equals(s2)){
            log.fine(String.format("Match found for teams '%s' & '%s'. Mixed order %s %s.", T1, T2, s1, s2));
            return true;
        }

        // Only check further if triggered to. This stops recurred calls from going further.
        if (!deep_check){
            return false;
        }

        // Removes FC from names and sees if they match.
        if (s1.contains("fc") || s2.contains("fc")){
            p1.remove("fc");
            p2.remove("fc");

            boolean success = same_team(String.join(" ", p1), String.join(" ", p2), false);
            if (success){
                log.fine(String.format("Match found for teams once FC removed '%s' & '%s'.", T1, T2));
                return true;
            }
        }



        log.fine(String.format("No match found for %s and %s.", T1, T2));
        return false;
    }


}
