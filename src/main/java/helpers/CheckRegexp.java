package helpers;

public class CheckRegexp {

    public static boolean checkZip(String string) {
        return string.matches("\\d+");
    }

    public static boolean checkPrice(String string) {
        return string.matches("\\d\\d\\d\\d\\$+");
    }


}
