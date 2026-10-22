package com.jxparallel.forge;

import java.util.Date;

/** Controller of the l10n tests: the values the translated texts of the samples take as arguments. */
public class L10nController {
    public final User user = new User();
    public int count = 2;
    public String gender = "female";
    public double price = 9.5;
    public Date when = new Date(0L);
    public String number = "12";

    public User getUser() {
        return user;
    }

    public int getCount() {
        return count;
    }

    public String getGender() {
        return gender;
    }

    public double getPrice() {
        return price;
    }

    public Date getWhen() {
        return when;
    }

    public String getNumber() {
        return number;
    }

    public static class User {
        public String name = "Ada";

        public String getName() {
            return name;
        }
    }
}
