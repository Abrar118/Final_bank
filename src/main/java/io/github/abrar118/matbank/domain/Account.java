package io.github.abrar118.matbank.domain;

public record Account(long id, long userId, AccountType type, String number, Money balance) {

    /** Account number with all but the last four digits hidden, e.g. "•••• 4821". */
    public String maskedNumber() {
        String digits = number.replaceAll("\\D", "");
        return "•••• " + digits.substring(Math.max(0, digits.length() - 4));
    }
}
