package org.alexmond.jhelm.gotemplate;

import lombok.Getter;

@Getter
public class Recipient {
    private final String name;
    private final String gift;
    private final boolean attended;

    public Recipient(String name, String gift, boolean attended) {
        this.name = name;
        this.gift = gift;
        this.attended = attended;
    }

}
