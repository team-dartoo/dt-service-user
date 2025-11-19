package dartoo.accountService.domain;

public enum Gender {
    MALE, FEMALE, UNKNOWN;

    public static Gender fromString(String gender) {
        if (gender.equalsIgnoreCase("male") || gender.equalsIgnoreCase("M")) {
            return MALE;
        } else if (gender.equalsIgnoreCase("female")  || gender.equalsIgnoreCase("F")) {
            return FEMALE;
        } else {
            return UNKNOWN;
        }
    }
}
